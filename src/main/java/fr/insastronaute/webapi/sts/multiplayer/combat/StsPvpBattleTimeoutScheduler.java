package fr.insastronaute.webapi.sts.multiplayer.combat;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatService.StsPvpCommandOutcome;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattle;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattleStatus;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Keeps a PvP battle from stalling on a player who stopped answering.
 *
 * <p>The policy is deliberately asymmetric, because the two silences it answers are not the same
 * one. A turn that runs out of time costs its player the turn and nothing else: someone thinking
 * too long, or reconnecting, should not lose the match for it. A player whose heartbeat has been
 * gone for the whole grace period has left, and the one still sitting there is owed the win rather
 * than an indefinite wait.
 *
 * <p>Both sweeps drive the same {@link StsPvpCombatService} a player's own commands go through, so
 * the engine stays the single source of truth: the expired turn is an ordinary {@code
 * EndTurnCommand} built from the stored state, and nothing here reimplements a rule.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.sts.pvp.timeout",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class StsPvpBattleTimeoutScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StsPvpBattleTimeoutScheduler.class);

    private final StsPvpBattleRepository battleRepository;
    private final StsPvpCombatService combatService;
    private final StsPvpCombatBroadcaster broadcaster;
    private final Duration disconnectGrace;
    private final AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;

    public StsPvpBattleTimeoutScheduler(
            StsPvpBattleRepository battleRepository,
            StsPvpCombatService combatService,
            StsPvpCombatBroadcaster broadcaster,
            @Value("${app.sts.pvp.timeout.disconnect-grace-seconds:120}") long disconnectGraceSeconds,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher
    ) {
        this.battleRepository = battleRepository;
        this.combatService = combatService;
        this.broadcaster = broadcaster;
        this.autoChallengeTriggerPublisher = autoChallengeTriggerPublisher;
        this.disconnectGrace = Duration.ofSeconds(disconnectGraceSeconds);
    }

    /**
     * One pass over the battles under way.
     *
     * <p>Transactional here rather than on the two sweeps: the scheduler calls them on itself, and
     * a call a bean makes on itself never goes through the proxy that would open the transaction.
     */
    @Scheduled(
            initialDelayString = "${app.sts.pvp.timeout.sweep-initial-delay-ms:30000}",
            fixedDelayString = "${app.sts.pvp.timeout.sweep-delay-ms:10000}"
    )
    @Transactional
    public void sweepBattles() {
        sweepExpiredTurns();
        sweepDisconnectedPlayers();
    }

    /** Ends the turn of every player whose deadline has passed. They lose the turn, not the match. */
    public void sweepExpiredTurns() {
        Instant now = TimeTravelContext.instantNow();
        forEachBattleUnderWay(battle -> endExpiredTurn(battle, now));
    }

    /** Settles every battle a player has been gone from for longer than the grace period. */
    public void sweepDisconnectedPlayers() {
        Instant lastAcceptableHeartbeat = TimeTravelContext.instantNow().minus(disconnectGrace);
        forEachBattleUnderWay(battle -> forfeitTheAbsent(battle, lastAcceptableHeartbeat));
    }

    /**
     * Runs {@code work} on each battle under way, one battle's failure never stopping the others.
     *
     * <p>The battle is loaded under the same write lock a player's command takes, so a sweep and
     * the move a player makes at that exact moment resolve one after the other rather than one
     * over the other.
     */
    private void forEachBattleUnderWay(Consumer<StsPvpBattle> work) {
        for (UUID battleId : battleRepository.findIdsByStatus(StsPvpBattleStatus.ACTIVE)) {
            try {
                battleRepository.findByIdWithLock(battleId).ifPresent(work);
            } catch (RuntimeException failure) {
                LOGGER.warn("PvP battle {} could not be swept: {}", battleId, failure.getMessage());
            }
        }
    }

    private void endExpiredTurn(StsPvpBattle battle, Instant now) {
        Instant deadline = battle.getTurnDeadline();
        if (deadline == null || deadline.isAfter(now)) {
            return;
        }
        Optional<CombatState> current = combatService.currentStateIfAny(battle);
        if (current.isEmpty()) {
            return;
        }

        CombatState state = current.get();
        // The engine's own guard decides who may act, so the command is built for the combatant it
        // would accept and on the revision the stored state is actually at.
        String activeCombatantId = StsPvpTurnGuard.activeCombatantId(state);
        UUID actingUserId = userIdOrNull(activeCombatantId);
        if (actingUserId == null) {
            // Le tour d'un combattant pilote par le serveur : il est joue dans la foulee de la
            // commande qui le lui rend, donc l'y trouver signifie qu'il est bloque. Rien a lui
            // prendre ici -- une echeance ne se retire qu'a un joueur.
            return;
        }

        StsPvpCommandOutcome outcome = combatService.applyCommand(
                battle,
                actingUserId,
                new EndTurnCommand(state.combatId(), state.revision(), activeCombatantId));
        battleRepository.save(battle);
        LOGGER.info("PvP battle {}: turn of {} ended on its deadline", battle.getId(), activeCombatantId);
        // Nothing a player did caused this, so the frame answers no action of theirs.
        broadcaster.publish(battle, outcome.state(), null);
    }

    /**
     * Declare forfait les joueurs qui ne donnent plus signe de vie.
     *
     * <p>Le silence se compte par equipe et non par joueur : une equipe ne perd que lorsque tous
     * ses joueurs sont partis. Un 2v2 ou un seul joueur ferme son onglet continue donc a se jouer
     * a trois, ce qui est le comportement voulu -- son equipier n'a rien abandonne.
     *
     * <p>Les sieges pilotes par le serveur sont ignores : ils ne battent jamais, et les compter
     * comme silencieux ferait perdre le raid a l'equipe du boss des la premiere passe.
     */
    private void forfeitTheAbsent(StsPvpBattle battle, Instant lastAcceptableHeartbeat) {
        List<StsPvpSeat> seats = combatService.seats(battle);
        Map<Integer, List<StsPvpSeat>> byTeam = seats.stream()
                .filter(seat -> !seat.ai())
                .collect(Collectors.groupingBy(StsPvpSeat::teamIndex));
        if (byTeam.isEmpty()) {
            return;
        }

        List<Integer> deadTeams = byTeam.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .allMatch(seat -> hasGoneSilent(battle, seat, lastAcceptableHeartbeat)))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (deadTeams.isEmpty()) {
            return;
        }

        battle.setTurnDeadline(null);
        if (deadTeams.size() >= byTeam.size()) {
            // Nobody is left to be declared the winner, and a rating cannot be moved to no one.
            battle.setStatus(StsPvpBattleStatus.ABANDONED);
            battle.setWinnerTeam(null);
            battleRepository.save(battle);
            LOGGER.info("PvP battle {} abandoned: every player is gone", battle.getId());
            return;
        }

        int loserTeam = deadTeams.get(0);
        int winnerTeam = byTeam.keySet().stream()
                .filter(team -> team != loserTeam)
                .findFirst()
                .orElseThrow();

        battle.setStatus(StsPvpBattleStatus.FINISHED);
        battle.setWinnerTeam(winnerTeam);

        // Chaque absent de l'equipe partie est sorti du combat, l'un apres l'autre : l'etat doit
        // porter la defaite de l'equipe entiere et pas seulement celle du premier nomme.
        List<CombatEvent> events = new ArrayList<>();
        CombatState settled = null;
        for (StsPvpSeat absent : byTeam.get(loserTeam)) {
            // Chaine sur l'etat que la concession precedente a produit, et non sur une relecture
            // de la bataille : c'est le seul ordre qui fasse sortir toute l'equipe du combat, et
            // il ne depend pas de ce qu'un stockage intermediaire aurait rendu.
            CombatState from = settled != null
                    ? settled
                    : combatService.currentStateIfAny(battle).orElse(null);
            if (from == null) {
                break;
            }
            StsPvpBattleSettlement.Conceded conceded =
                    StsPvpBattleSettlement.conceded(from, absent.combatantId());
            if (!conceded.applied()) {
                continue;
            }
            settled = conceded.state();
            combatService.storeState(battle, settled);
            events.addAll(conceded.events());
        }

        // Le meme reglement que pour un combat que le moteur termine : classement, jetons et
        // trace. Cette veille en avait sa propre copie, et deux baremes qui se ressemblent
        // finissent par ne plus se ressembler.
        combatService.settleRewards(battle, winnerTeam);
        battleRepository.save(battle);

        LOGGER.info("PvP battle {} forfeited by team {}", battle.getId(), loserTeam);
        // The end is announced before the state it ends on, as every other event is: the client
        // reads the outcome from the CombatEnded, and a state alone leaves it showing a fight
        // still under way. Nothing a player did caused this, so the frames answer no action.
        if (!events.isEmpty()) {
            broadcaster.publishEvents(battle, events, null);
        }
        if (settled != null) {
            broadcaster.publish(battle, settled, null);
        }
    }

    /**
     * @return true when {@code participant} has not been heard from since
     *         {@code lastAcceptableHeartbeat}, a heartbeat that was never recorded counting as
     *         silence the same way {@code StsPvpService} already counts it
     */
    private boolean hasGoneSilent(
            StsPvpBattle battle,
            StsPvpSeat participant,
            Instant lastAcceptableHeartbeat
    ) {
        JsonNode heartbeat = battle.getHeartbeat();
        if (heartbeat == null || !heartbeat.isObject()) {
            return true;
        }
        long seenAt = heartbeat.path(participant.userId().toString()).asLong(0L);
        return seenAt <= 0L || Instant.ofEpochMilli(seenAt).isBefore(lastAcceptableHeartbeat);
    }

    private static UUID userIdOrNull(String combatantId) {
        try {
            return UUID.fromString(combatantId);
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }
}
