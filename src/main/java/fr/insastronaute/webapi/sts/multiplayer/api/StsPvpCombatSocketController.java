package fr.insastronaute.webapi.sts.multiplayer.api;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCommandRejectedException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.sts.combat.api.dto.CombatCommandEnvelope;
import fr.insastronaute.webapi.sts.combat.api.dto.CombatMessageEnvelope;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatBroadcaster;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatService;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatService.StsPvpCommandOutcome;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatView;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattle;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattleCommand;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleCommandRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The socket end of an authoritative PvP combat.
 *
 * <p>Every player is served on their own user queue rather than on a shared topic, and that is a
 * rule rather than a preference: one broadcast frame cannot carry two different states, while the
 * authoritative state holds both hands and the seed the entire future draw order can be computed
 * from. What each player is sent is {@link StsPvpCombatView}'s projection for them, so the private
 * half of the combat never leaves the server by construction instead of by discipline.
 *
 * <p>The protocol is the one the front already speaks for PvE: the same {@link
 * CombatCommandEnvelope} in, the same {@link CombatMessageEnvelope} out on a {@code
 * /user/queue/sts/...} destination, and the same refusal of a frame that names another combat,
 * carries no action identifier or speaks an unknown protocol version.
 */
@Controller
@ConditionalOnProperty(name = "app.sts.pvp.enabled", havingValue = "true", matchIfMissing = true)
public class StsPvpCombatSocketController {

    private static final Logger LOGGER = LoggerFactory.getLogger(StsPvpCombatSocketController.class);

    private static final int SUPPORTED_PROTOCOL_VERSION = 1;

    private final StsPvpBattleRepository battleRepository;
    private final StsPvpBattleCommandRepository commandRepository;
    private final StsPvpCombatService combatService;
    private final UserRepository userRepository;
    private final StsPvpCombatBroadcaster broadcaster;

    public StsPvpCombatSocketController(
            StsPvpBattleRepository battleRepository,
            StsPvpBattleCommandRepository commandRepository,
            StsPvpCombatService combatService,
            UserRepository userRepository,
            StsPvpCombatBroadcaster broadcaster
    ) {
        this.battleRepository = battleRepository;
        this.commandRepository = commandRepository;
        this.combatService = combatService;
        this.userRepository = userRepository;
        this.broadcaster = broadcaster;
    }

    /**
     * Receives one command from one player.
     *
     * <p>Every path out of here has to say something back. A command the server threw out used to
     * die in silence: the PvP channel had no {@code COMMAND_REJECTED} at all, so the client stayed
     * on the revision it had sent, waiting for an answer that was never coming, and a player with
     * no move left had no way to unstick the fight.
     *
     * <p>The refusal is answered and then <em>rethrown</em>, because the client needs to hear
     * about it and this handler is {@code @Transactional}: only the rethrow gets both. Swallowing
     * the failure would commit whatever the failed command left half-written.
     */
    @MessageMapping("/sts/pvp/battles/{battleId}/commands")
    @Transactional
    public void receiveCommand(
            @DestinationVariable String battleId,
            @Payload CombatCommandEnvelope command,
            Principal principal
    ) {
        try {
            handleCommand(battleId, command, principal);
        } catch (RuntimeException refused) {
            LOGGER.error("STS PvP combat command failed (battle={}, action={})",
                    battleId, command == null ? null : command.actionId(), refused);
            answerRefusal(battleId, command, principal, refused);
            throw refused;
        }
    }

    private void handleCommand(
            String battleId,
            CombatCommandEnvelope command,
            Principal principal
    ) {
        if (command.protocolVersion() != SUPPORTED_PROTOCOL_VERSION) {
            throw new BadRequestException("Unsupported combat protocol version.");
        }
        if (!battleId.equals(command.combatId())) {
            throw new BadRequestException("Combat command destination mismatch.");
        }
        if (command.actionId() == null || command.actionId().isBlank()) {
            throw new BadRequestException("Combat command action identifier is required.");
        }
        long expectedRevision = expectedRevision(command);
        UUID battleUuid = battleUuid(battleId);

        // The actor is the authenticated player and nothing else: an actor read off the payload
        // would let a client play its opponent's turn.
        AppUser actor = userRepository.findByKeycloakSub(principal.getName())
                .orElseThrow(() -> new NotFoundException("Joueur introuvable : " + principal.getName()));
        StsPvpBattle battle = battleRepository.findByIdWithLock(battleUuid)
                .orElseThrow(() -> new NotFoundException("Combat PVP introuvable : " + battleId));

        // The same frame can be delivered twice. Answering the second delivery with the state the
        // first produced is what keeps a card from being played twice on a flaky connection.
        Optional<StsPvpBattleCommand> alreadyAnswered = commandRepository
                .findByBattleIdAndActingUserIdAndActionId(battleUuid, actor.getId(), command.actionId());
        if (alreadyAnswered.isPresent()) {
            republish(actor, battle, command.actionId());
            return;
        }

        CombatState state = combatService.currentState(battle);
        StsPvpCommandOutcome outcome = combatService.applyCommand(
                battle, actor.getId(), engineCommand(state, actor, expectedRevision, command));

        battleRepository.save(battle);
        commandRepository.save(StsPvpBattleCommand.accepted(
                battleUuid, actor.getId(), command.actionId(), outcome.state().revision()));

        broadcaster.publishEvents(battle, outcome.events(), command.actionId());
        broadcaster.publish(battle, outcome.state(), command.actionId());
    }

    /**
     * Tells the player their command died, without telling them how.
     *
     * <p>A code and a flat sentence are all that leaves the server. The exception's own message is
     * operator material — it may quote the state, and the state is exactly what this channel
     * spends its effort not handing over — and it has already gone to the log. Publishing is
     * shielded so a broker that is down cannot swallow the original failure on its way out.
     */
    private void answerRefusal(
            String battleId,
            CombatCommandEnvelope command,
            Principal principal,
            RuntimeException refused
    ) {
        // Without an action identifier there is nothing for the client to match the answer
        // against, and a command it never tracked is a command it is not waiting on.
        if (command == null || command.actionId() == null || command.actionId().isBlank()
                || principal == null || principal.getName() == null) {
            return;
        }
        try {
            broadcaster.sendRejection(
                    principal.getName(),
                    battleId,
                    command.expectedRevision(),
                    command.actionId(),
                    refusalReason(refused));
        } catch (RuntimeException notDelivered) {
            LOGGER.warn("Could not tell {} that PvP combat command {} was refused",
                    principal.getName(), command.actionId(), notDelivered);
        }
    }

    /**
     * Why a command was refused, in the only vocabulary that crosses the wire.
     *
     * <p>The type of the failure is what carries the meaning, since PvP has no rejection path that
     * is not an exception: the turn guard throws, the engine's own rejection is rethrown as a bad
     * request by the service, and a settled battle is a conflict. A conflict is answered
     * {@code OUT_OF_SYNC} rather than named more precisely because every one of them — a stale
     * revision, a battle a sweep has already settled, a combat with nobody left to act — asks the
     * client for the same move: read the combat again.
     */
    /** Visible for tests: the mapping is the contract, and a contract deserves to be pinned. */
    static Map<String, String> refusalReasonFor(RuntimeException refused) {
        return refusalReason(refused);
    }

    private static Map<String, String> refusalReason(RuntimeException refused) {
        if (refused instanceof ForbiddenException) {
            return Map.of("code", "NOT_ACTOR_TURN",
                    "message", "It is not your turn to play.");
        }
        if (refused instanceof ConflictException) {
            return Map.of("code", "OUT_OF_SYNC",
                    "message", "This combat has moved on since this command was sent.");
        }
        if (refused instanceof NotFoundException) {
            return Map.of("code", "COMBAT_NOT_FOUND",
                    "message", "This combat is no longer available.");
        }
        // Asked before the bare bad request below, which it is a kind of: the engine already
        // named the rule that was broken, and the client can act on "not enough energy" in a way
        // it cannot act on "invalid command".
        if (refused instanceof StsPvpCommandRejectedException rejected) {
            return Map.of("code", rejected.code().name(),
                    "message", rejected.getMessage() == null ? "" : rejected.getMessage());
        }
        if (refused instanceof BadRequestException) {
            return Map.of("code", "INVALID_COMMAND",
                    "message", "This command was not valid.");
        }
        return Map.of("code", "INTERNAL_ERROR",
                "message", "The server could not process this command.");
    }

    /**
     * Answers a command this battle has already resolved.
     *
     * <p>Only the player whose frame was repeated is answered: the other one was served by the
     * first delivery and a second copy would only make it re-render. What travels is the current
     * state rather than the one the command produced, because the authoritative state is always
     * the one that counts and a client that missed an answer needs the combat as it stands.
     *
     * <p>The combat events of that first delivery are gone; the state carries the outcome, which
     * is what a client needs to be right again.
     */
    private void republish(AppUser actor, StsPvpBattle battle, String actionId) {
        broadcaster.send(
                actor.getKeycloakSub(), actor.getId(), battle, combatService.currentState(battle), actionId);
    }

    /**
     * The engine command a socket frame asks for.
     *
     * <p>The combat is named by the state rather than by the frame, and the actor by the
     * authenticated player rather than by the payload, so neither can be aimed elsewhere.
     */
    private static CombatCommand engineCommand(
            CombatState state,
            AppUser actor,
            long expectedRevision,
            CombatCommandEnvelope command
    ) {
        String type = command.type() == null ? "" : command.type().trim().toUpperCase();
        String actorId = actor.getId().toString();
        return switch (type) {
            case "END_TURN" -> new EndTurnCommand(state.combatId(), expectedRevision, actorId);
            case "PLAY_CARD" -> new PlayCardCommand(
                    state.combatId(),
                    expectedRevision,
                    actorId,
                    requireCard(stringValue(command.payload(), "cardInstanceId")),
                    stringList(command.payload(), "targetIds"),
                    stringList(command.payload(), "selectedCardInstanceIds"));
            default -> throw new BadRequestException("Type de commande de combat PVP inconnu.");
        };
    }

    private static String requireCard(String cardInstanceId) {
        if (cardInstanceId == null || cardInstanceId.isBlank()) {
            throw new BadRequestException("La carte jouée est requise.");
        }
        return cardInstanceId;
    }

    private static String stringValue(Object payload, String field) {
        if (payload instanceof Map<?, ?> map && map.get(field) instanceof String value) {
            return value;
        }
        return null;
    }

    private static List<String> stringList(Object payload, String field) {
        if (payload instanceof Map<?, ?> map && map.get(field) instanceof List<?> values) {
            List<String> strings = new ArrayList<>();
            values.forEach(value -> strings.add(String.valueOf(value)));
            return List.copyOf(strings);
        }
        return List.of();
    }

    private static long expectedRevision(CombatCommandEnvelope command) {
        try {
            return Long.parseLong(command.expectedRevision());
        } catch (NumberFormatException | NullPointerException notANumber) {
            throw new BadRequestException("Combat command expected revision must be a number.");
        }
    }

    private static UUID battleUuid(String battleId) {
        try {
            return UUID.fromString(battleId);
        } catch (IllegalArgumentException notAnId) {
            throw new BadRequestException("Combat PVP identifier is not a valid id.");
        }
    }
}
