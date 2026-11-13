package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.event.TurnStarted;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The turn goes to the first living entry on the timeline, never to a corpse standing before it.
 *
 * <p>A combatant killed by its own statuses keeps its place on the timeline, and the state says so:
 * it asks that the active combatant be the first <em>living</em> entry, and tolerates the dead
 * everywhere on the list. The engine read the first entry instead, so a Burn or a Poison that
 * finished off the combatant whose turn was closing handed the next turn to that corpse. The state
 * refused it — {@code active combatant must be living} — out of the message handler, which left the
 * command unanswered and the player's combat stuck on that revision for good. It only shows when
 * the dead combatant's own team survives it, since otherwise the combat is over and the handler
 * never gets as far as choosing.
 */
class CombatEngineDeadTimelineHeadTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000e2");
    private static final String HERO = "hero";
    private static final String ALLY = "ally";
    private static final String ENEMY_A = "enemyA";
    private static final String ENEMY_B = "enemyB";
    private static final String PLAYERS = "players";
    private static final String ENEMIES = "enemies";

    /**
     * A stunned enemy is poisoned to death on the turn it was too stunned to take, and its own team
     * survives it. It is rescheduled soonest anyway, so the loop that skips stunned turns read the
     * corpse back off the head of the timeline and handed it the turn.
     */
    @Test
    void skipsTheStunnedCombatantItsOwnStatusesKilledInsteadOfHandingItTheTurn() {
        CombatState state = new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(
                        combatant(HERO, PLAYERS, ControllerType.HUMAN, 30, 100, List.of()),
                        combatant(ENEMY_A, ENEMIES, ControllerType.AI, 2, 10,
                                List.of(stun(), poison(5))),
                        combatant(ENEMY_B, ENEMIES, ControllerType.AI, 30, 100, List.of())),
                List.of(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry(ENEMY_A, 10, 1),
                        new TimelineEntry(ENEMY_B, 1000, 2)),
                HERO);

        CombatResult result = endTurn(state, HERO);

        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.state().winnerTeamId()).isNull();
        assertThat(combatant(result.state(), ENEMY_A).hp()).isZero();
        assertThat(result.state().activeCombatantId()).isEqualTo(HERO);
        assertThat(result.state().timeline())
                .extracting(TimelineEntry::combatantId)
                .containsExactly(ENEMY_A, HERO, ENEMY_B);
        assertThat(result.events())
                .filteredOn(TurnStarted.class::isInstance)
                .extracting(event -> ((TurnStarted) event).combatantId())
                .containsExactly(ENEMY_A, HERO);
        assertThat(result.events()).noneMatch(CombatEnded.class::isInstance);
    }

    /**
     * No stun needed: the player's own Burn kills it as its turn closes, and the entry it is put
     * back on the timeline with is the soonest of all. The handler read that entry and gave the
     * next turn to the body it had just made.
     */
    @Test
    void handsTheTurnOnPastTheCombatantItsOwnEndOfTurnBurnKilled() {
        CombatState state = new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(
                        combatant(HERO, PLAYERS, ControllerType.HUMAN, 2, 10, List.of(burn(5))),
                        combatant(ALLY, PLAYERS, ControllerType.HUMAN, 30, 100, List.of()),
                        combatant(ENEMY_A, ENEMIES, ControllerType.AI, 30, 100, List.of())),
                List.of(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry(ALLY, 500, 1),
                        new TimelineEntry(ENEMY_A, 1000, 2)),
                HERO);

        CombatResult result = endTurn(state, HERO);

        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.state().winnerTeamId()).isNull();
        assertThat(combatant(result.state(), HERO).hp()).isZero();
        assertThat(result.state().activeCombatantId()).isEqualTo(ALLY);
        assertThat(result.state().timeline())
                .extracting(TimelineEntry::combatantId)
                .containsExactly(HERO, ALLY, ENEMY_A);
        assertThat(result.events())
                .filteredOn(TurnStarted.class::isInstance)
                .extracting(event -> ((TurnStarted) event).combatantId())
                .containsExactly(ALLY);
        assertThat(result.events()).noneMatch(CombatEnded.class::isInstance);
    }

    private static CombatantState combatant(CombatState state, String combatantId) {
        return state.combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static CombatResult endTurn(CombatState state, String actorId) {
        CombatEngine engine =
                new CombatEngine(new CardCatalog("v1", Map.of()), new RelicEngine(List.of()));
        CombatResult result = engine.handle(
                state, new EndTurnCommand(COMBAT_ID, state.revision(), actorId));
        assertThat(result.accepted()).isTrue();
        return result;
    }

    /** Burn strikes for its remaining duration as the turn it is carried through closes. */
    private static StatusInstance burn(int damage) {
        return new StatusInstance("Burn", 0, damage, null, 0);
    }

    /** Poison strikes for its remaining duration as its holder's turn opens. */
    private static StatusInstance poison(int damage) {
        return new StatusInstance("Poison", 0, damage, null, 0);
    }

    /** Stun costs its holder the one turn it is carried through. */
    private static StatusInstance stun() {
        return new StatusInstance("Stun", 1, 1, null, 0);
    }

    private static CombatantState combatant(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int hp,
            int turnDelayTicks,
            List<StatusInstance> statuses
    ) {
        return new CombatantState(
                combatantId,
                teamId,
                controllerType,
                30,
                hp,
                0,
                0,
                3,
                turnDelayTicks,
                new CardPiles(
                        List.of(new CardInstance(combatantId + "-spare", "any")),
                        List.of(),
                        List.of(),
                        List.of()),
                statuses,
                List.of(),
                null,
                0);
    }
}
