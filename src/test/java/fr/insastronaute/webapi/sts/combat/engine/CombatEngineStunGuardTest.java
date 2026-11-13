package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

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
 * The skip loop used to be bounded by {@code combatants.size()}, on the theory that it counts the
 * same thing the opening loop twenty lines above it counts. It does not: the opening loop counts
 * openings, at most one per combatant, but this one counts skips, and a single {@code Stun} of
 * duration N needs N of them on the one combatant carrying it, whoever else is waiting behind it.
 * Bounding skips by combatant count made "every combatant is stunned" — a state Stun's own
 * countdown resolves on its own, the same as any other status — indistinguishable from a Stun
 * that can never resolve at all, and threw on both.
 *
 * <p>Only the second is a real impossibility: a Stun whose duration is negative never counts down
 * — nothing decrements it and nothing expires it on its own — so the combatant carrying it is
 * barred from acting forever. That is what must throw, named for the combatant it is stuck on.
 */
class CombatEngineStunGuardTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000e3");
    private static final String HERO = "hero";
    private static final String ENEMY_A = "enemyA";
    private static final String ENEMY_B = "enemyB";
    private static final String ENEMY_C = "enemyC";
    private static final String PLAYERS = "players";
    private static final String ENEMIES = "enemies";

    /**
     * Every combatant stunned at once, each for long enough to outlive more than one skipped
     * turn (duration two), is not the impossible case: each skip counts its Stun down like any
     * other status, and the combat carries on. All three share the same turn delay so the loop
     * round-robins through every one of them rather than looping back on whichever has the
     * shortest delay — proof that the resolution does not depend on who happens to come up
     * again first.
     *
     * <p>Before the fix, this same setup threw: the loop was bounded by
     * {@code combatants.size()} skips (three, plus one), and six are needed here — two per
     * combatant — before the first of them (enemyA, the soonest on the timeline) finally clears
     * its Stun and takes a real turn.
     */
    @Test
    void everyoneStunnedAtOnceResolvesOnItsOwnInsteadOfThrowing() {
        CombatState state = new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(
                        combatant(HERO, PLAYERS, ControllerType.HUMAN, 30, 100, List.of(stun(2))),
                        combatant(ENEMY_A, ENEMIES, ControllerType.AI, 30, 100, List.of(stun(2))),
                        combatant(ENEMY_B, ENEMIES, ControllerType.AI, 30, 100, List.of(stun(2)))),
                List.of(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry(ENEMY_A, 10, 1),
                        new TimelineEntry(ENEMY_B, 20, 2)),
                HERO);

        CombatResult result = engine().handle(
                state, new EndTurnCommand(COMBAT_ID, state.revision(), HERO));

        assertThat(result.accepted()).isTrue();
        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.events()).noneMatch(CombatEnded.class::isInstance);
        // hero's own end-of-turn tick (run once as its own turn closes, before this loop is
        // ever entered) already counts its Stun from two down to one, so it only needs one more
        // skip's worth of ticking to clear -- and by the time that happens (its entry having
        // been rescheduled behind enemyA's and enemyB's), those two still need one skip each of
        // their own. hero is the one still stunned the fewest times over, so it is the one a
        // real turn is finally opened for: hand dealt and all, not one more lost turn.
        assertThat(result.state().activeCombatantId()).isEqualTo(HERO);
        assertThat(combatant(result.state(), HERO).statuses())
                .as("hero's Stun should have counted all the way down and expired")
                .noneMatch(status -> "Stun".equals(status.statusType()));
        assertThat(combatant(result.state(), HERO).piles().hand())
                .as("a real turn deals a hand; a skipped one does not")
                .isNotEmpty();
        assertThat(result.events())
                .filteredOn(TurnStarted.class::isInstance)
                .as("one TurnStarted per skip (five) plus the one real turn hero is finally given")
                .hasSize(6);
    }

    /**
     * A Stun carried at a negative duration is not a long one, it is one that never counts down:
     * {@code countDown} refuses to touch a duration that is already {@code <= 0}, and nothing
     * else here decrements or expires it either. No number of skips resolves it, so the
     * combatant it never lets go of is barred from acting forever — which is the one thing this
     * loop must refuse rather than spin on.
     */
    @Test
    void aStunThatNeverCountsDownIsRefusedRatherThanLoopedOnForever() {
        CombatState state = new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(
                        combatant(HERO, PLAYERS, ControllerType.HUMAN, 30, 100, List.of()),
                        combatant(ENEMY_A, ENEMIES, ControllerType.AI, 30, 100, List.of(stun(-1))),
                        combatant(ENEMY_B, ENEMIES, ControllerType.AI, 30, 100, List.of())),
                List.of(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry(ENEMY_A, 10, 1),
                        new TimelineEntry(ENEMY_B, 20, 2)),
                HERO);

        assertThatIllegalStateException()
                .isThrownBy(() -> engine().handle(
                        state, new EndTurnCommand(COMBAT_ID, state.revision(), HERO)))
                .withMessageContaining("stun")
                .withMessageContaining(ENEMY_A);
    }

    /**
     * The two tests above are the isolated extremes: everyone resolves, or the one combatant in
     * the whole combat never does. A real combat looks like this instead -- most of it resolving
     * normally while exactly one Stun never does -- and it is the only shape that exercises the
     * progress check across more than a single combatant: enemyB and enemyC each count down for
     * real, in the same command, before enemyA is ever reached.
     *
     * <p>enemyB and enemyC sit ahead of enemyA on the timeline and share its turn delay, so both
     * are skipped once (duration two down to one, genuine progress, no throw) before enemyA -- a
     * negative duration that will never count down -- is reached and caught on its own first
     * skip. hero never enters the loop at all here: it is not stunned, so ending its turn would
     * hand the turn straight to it if the loop ever got that far, which is exactly why it does
     * not.
     */
    @Test
    void oneUnresolvableStunIsCaughtWhileOthersResolveAroundIt() {
        CombatState state = new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(
                        combatant(HERO, PLAYERS, ControllerType.HUMAN, 30, 100, List.of()),
                        combatant(ENEMY_B, ENEMIES, ControllerType.AI, 30, 100, List.of(stun(2))),
                        combatant(ENEMY_C, ENEMIES, ControllerType.AI, 30, 100, List.of(stun(2))),
                        combatant(ENEMY_A, ENEMIES, ControllerType.AI, 30, 100, List.of(stun(-1)))),
                List.of(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry(ENEMY_B, 10, 1),
                        new TimelineEntry(ENEMY_C, 20, 2),
                        new TimelineEntry(ENEMY_A, 30, 3)),
                HERO);

        assertThatIllegalStateException()
                .isThrownBy(() -> engine().handle(
                        state, new EndTurnCommand(COMBAT_ID, state.revision(), HERO)))
                .withMessageContaining("stun")
                .withMessageContaining(ENEMY_A);
    }

    private static CombatEngine engine() {
        return new CombatEngine(new CardCatalog("v1", Map.of()), new RelicEngine(List.of()));
    }

    private static CombatantState combatant(CombatState state, String combatantId) {
        return state.combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    /** Stun costs its holder the turn it is carried through; a positive duration counts down. */
    private static StatusInstance stun(int duration) {
        return new StatusInstance("Stun", 1, duration, null, 0);
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
