package fr.insastronaute.webapi.sts.multiplayer.combat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.sts.combat.api.dto.CombatMessageEnvelope;
import fr.insastronaute.webapi.sts.combat.event.CardDrawn;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.PileShuffled;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattle;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Sends a PvP combat to the two players sitting in it, one projection each.
 *
 * <p>Every player is served on their own user queue rather than on a shared topic, and that is a
 * rule rather than a preference: one broadcast frame cannot carry two different states, while the
 * authoritative state holds both hands and the seed the entire future draw order can be computed
 * from. What each player is sent is {@link StsPvpCombatView}'s projection for them, so the private
 * half of the combat never leaves the server by construction instead of by discipline.
 *
 * <p>The delivery lives here rather than in the socket controller because a command from a player
 * is not the only thing that moves a combat: a turn the timeout sweep ends moves it just as much,
 * and a second copy of the destination and the envelope is how the two would start disagreeing.
 */
@Component
public class StsPvpCombatBroadcaster {

    static final String QUEUE_PREFIX = "/queue/sts/pvp/battles/";
    static final int PROTOCOL_VERSION = 1;

    private static final String STATE_UPDATED = "STATE_UPDATED";
    private static final String COMBAT_EVENT = "COMBAT_EVENT";
    private static final String COMMAND_REJECTED = "COMMAND_REJECTED";
    private static final String HAND = "HAND";
    private static final String DRAW = "DRAW";

    private final StsPvpCombatService combatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public StsPvpCombatBroadcaster(
            StsPvpCombatService combatService,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper
    ) {
        this.combatService = combatService;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends {@code state} to every player of {@code battle}, each one seeing their own half of it.
     *
     * @param causationActionId the action this state answers, or null when nothing a player did
     *                          caused it — a turn the server ended on a deadline, for one
     */
    public void publish(StsPvpBattle battle, CombatState state, String causationActionId) {
        Objects.requireNonNull(battle, "battle");
        Objects.requireNonNull(state, "state");

        // Les sieges joues par le serveur -- un boss de raid, un renfort -- n'ont pas de
        // socket : leur envoyer une projection s'adresserait a personne.
        for (StsPvpSeat seat : combatService.playerSeats(battle)) {
            send(seat.keycloakSub(), seat.userId(), battle, state, causationActionId);
        }
    }

    /**
     * Sends the events of a command to the players entitled to each of them.
     *
     * <p>They go out before the state, as they do in PvE: they are what the client animates with,
     * and an animation that arrives after the state it explains has nothing left to play. Each
     * player is filtered on their own, because the same list is not the same list for both.
     *
     * @param causationActionId the action these events came out of, or null when nothing a player
     *                          did caused them
     */
    public void publishEvents(StsPvpBattle battle, List<CombatEvent> events, String causationActionId) {
        Objects.requireNonNull(battle, "battle");
        Objects.requireNonNull(events, "events");
        if (events.isEmpty()) {
            return;
        }

        String battleId = battle.getId().toString();
        for (StsPvpSeat seat : combatService.playerSeats(battle)) {
            for (CombatEvent event : visibleTo(events, seat.combatantId())) {
                messagingTemplate.convertAndSendToUser(
                        seat.keycloakSub(),
                        QUEUE_PREFIX + battleId,
                        new CombatMessageEnvelope(
                                PROTOCOL_VERSION,
                                battleId,
                                Long.toString(event.revision()),
                                COMBAT_EVENT,
                                causationActionId,
                                serialized(event)));
            }
        }
    }

    /**
     * The events {@code viewerCombatantId} is entitled to see.
     *
     * <p>An event that names a card in a pile this viewer cannot see is dropped whole rather
     * than redacted: a CardDrawn with its definitionId removed still says a card was drawn and
     * from where, and the client has the pile counts in the view for that.
     *
     * <p>Three families name cards: what was drawn, what moved between piles, and the order a
     * reshuffle produced. The last one is the one worth stating out loud, because its payload is
     * a list of opaque ids that reads as harmless -- and is not.
     */
    public static List<CombatEvent> visibleTo(
            List<CombatEvent> events, String viewerCombatantId) {
        return events.stream()
                .filter(event -> !hidesACardFrom(event, viewerCombatantId))
                .toList();
    }

    private static boolean hidesACardFrom(CombatEvent event, String viewerCombatantId) {
        if (event instanceof CardDrawn drawn) {
            return !drawn.combatantId().equals(viewerCombatantId);
        }
        if (event instanceof PileShuffled shuffled) {
            // The whole new order of a pile, which for the draw pile is the owner's next draws
            // written out in advance. The ids are opaque and that is not protection: an opponent's
            // discard pile is shown in full, and every CardPlayed ties an instance id to its
            // definition, so the mapping is there for anyone who keeps notes.
            return !shuffled.combatantId().equals(viewerCombatantId);
        }
        if (event instanceof CardMoved moved) {
            boolean intoAHiddenPile = HAND.equals(moved.toPile()) || DRAW.equals(moved.toPile());
            boolean outOfAHiddenPile = HAND.equals(moved.fromPile()) || DRAW.equals(moved.fromPile());
            return (intoAHiddenPile || outOfAHiddenPile)
                    && !moved.combatantId().equals(viewerCombatantId);
        }
        return false;
    }

    /**
     * The event as the client reads it: its fields plus the {@code eventType} discriminator the
     * PvE channel already carries, since a frame without it is one the client cannot dispatch and
     * silently drops.
     */
    private Object serialized(CombatEvent event) {
        ObjectNode node = objectMapper.valueToTree(event);
        node.put("eventType", event.getClass().getSimpleName());
        return objectMapper.convertValue(node, Object.class);
    }

    /**
     * Tells one player that a command of theirs was not played.
     *
     * <p>Only that player: their opponent asked for nothing and a refusal addressed to somebody
     * else is noise at best. The frame carries {@code causationActionId} because that is the only
     * handle the client has on the command it is waiting for — without it the answer belongs to no
     * command in particular and the client stays blocked on the revision it sent.
     *
     * <p>{@code reason} is a code and a flat sentence and nothing else. It is built from the type
     * of the failure rather than from the combat, so no refusal can become a way to read a state
     * the sender was refused in the first place.
     *
     * @param battleId the battle as the client named it, since a refusal can happen before the
     *                 battle has been read — or because it could not be read at all
     * @param revision the revision the refused command claimed, which is where the client still is
     */
    public void sendRejection(
            String keycloakSub,
            String battleId,
            String revision,
            String causationActionId,
            Map<String, String> reason
    ) {
        Objects.requireNonNull(keycloakSub, "keycloakSub");
        Objects.requireNonNull(battleId, "battleId");
        Objects.requireNonNull(causationActionId, "causationActionId");

        messagingTemplate.convertAndSendToUser(
                keycloakSub,
                QUEUE_PREFIX + battleId,
                new CombatMessageEnvelope(
                        PROTOCOL_VERSION,
                        battleId,
                        revision,
                        COMMAND_REJECTED,
                        causationActionId,
                        Map.copyOf(Objects.requireNonNull(reason, "reason"))));
    }

    /** Sends {@code state} to a single player, projected for them.
     *
     * <p>The battle rather than its id, because the deadline the view carries is the battle's own
     * and reading it here is what keeps the deadline the client counts down to the same one the
     * timeout sweep will act on. The server's clock travels beside it through
     * {@link TimeTravelContext}, the same clock the service computes the deadline against.
     */
    public void send(
            String keycloakSub,
            UUID userId,
            StsPvpBattle battle,
            CombatState state,
            String causationActionId
    ) {
        StsPvpCombatView view = StsPvpCombatView.forViewer(
                state,
                userId.toString(),
                battle.getTurnDeadline(),
                TimeTravelContext.instantNow(),
                // Le même historique pour les deux joueurs : c'est ce qui en fait un historique
                // partagé plutôt que deux comptes tenus séparément.
                combatService.cardHistory(battle));
        messagingTemplate.convertAndSendToUser(
                keycloakSub,
                QUEUE_PREFIX + battle.getId(),
                new CombatMessageEnvelope(
                        PROTOCOL_VERSION,
                        battle.getId().toString(),
                        Long.toString(view.revision()),
                        STATE_UPDATED,
                        causationActionId,
                        view));
    }
}
