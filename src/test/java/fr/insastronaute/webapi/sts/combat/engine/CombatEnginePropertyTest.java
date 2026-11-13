package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.CardPlayed;
import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.event.HealApplied;
import fr.insastronaute.webapi.sts.combat.event.HpLost;
import fr.insastronaute.webapi.sts.combat.event.TurnStarted;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Plays combats to their end, over and over, from seeds.
 *
 * <p>Written because the worst defect of the six this reform follows never threw and
 * never logged: a won combat was recorded as a draw because a turn opened behind a
 * corpse and killed somebody else. A legal state that says the wrong thing is
 * invisible to an example test, and every seed here is an example nobody thought of.
 *
 * <p>It only became that once its oracle stopped agreeing with itself. It used to compare the
 * winner the state recorded against {@link fr.insastronaute.webapi.sts.combat.model.CombatProgress}
 * recomputed over that same state's combatants — the very function the state had been built from,
 * asked the same question about the same data — and then walk the losing side checking nobody was
 * left standing, which the state refuses to be built without. The bug it was written for would
 * have passed it green.
 *
 * <p>The winner is worked out from the events instead, which is a different account of the same
 * combat: hit points are followed through the stream in order, and the combat is decided at the
 * first event that leaves fewer than two teams standing. The engine has to have ended it there —
 * once, on its last revision, with that winner. Ending it later, or one death later, is exactly
 * how a won combat became a draw.
 */
class CombatEnginePropertyTest {

    private static final int SEEDS = 2000;

    /** Beyond this a combat is not slow, it is stuck. */
    private static final int COMMAND_LIMIT = 500;

    private static final String PLAYER_TEAM = "player";
    private static final String ENEMY_TEAM = "enemy";

    /**
     * Played once, read by every test below: two thousand combats are worth playing once, and
     * each test asks a different question of the same run.
     */
    private static List<Ending> endings = List.of();

    /**
     * A seed that broke, kept rather than thrown where it happened: thrown out of the fixture it
     * would be reported as an initialisation error against no test in particular, and every one of
     * these tests is about the run that failed.
     */
    private static AssertionError brokenSeed;

    @BeforeAll
    static void playEverySeed() {
        List<Ending> played = new ArrayList<>();
        for (int seed = 0; seed < SEEDS; seed++) {
            try {
                played.add(playToEnding(seed, actsOn(seed)));
            } catch (AssertionError | RuntimeException failure) {
                // Every failure path names its seed, including one the engine never meant to
                // reach: an exception thrown from engine.handle(...) itself, which would
                // otherwise cross this loop bare and turn a ten-minute reproduction into a
                // day-long one. The original failure is the cause, never swallowed.
                brokenSeed = new AssertionError("seed %d failed: %s"
                        .formatted(seed, failure.getMessage()), failure);
                break;
            }
        }
        endings = List.copyOf(played);
    }

    /** The run, or the seed that stopped it. */
    private static List<Ending> endings() {
        if (brokenSeed != null) {
            throw brokenSeed;
        }
        return endings;
    }

    /** Half the seeds act before ending their turn; the other half only ever end turns. */
    private static boolean actsOn(int seed) {
        return seed % 2 == 0;
    }

    /**
     * Every seed reaches an ending the event stream agrees with. The per-seed assertions live in
     * {@link #playToEnding}; this is where their failure is reported from.
     */
    @Test
    void everySeedReachesAnEndingItsOwnEventsAgreeWith() {
        assertThat(endings()).hasSize(SEEDS);
    }

    /**
     * A scenario family whose combats are over in a command or two proves nothing about a reform
     * that lives in how turns change hands. Ending turns alone used to kill everybody on their
     * first, so no combat ever reached a second round.
     */
    @Test
    void playsCombatsThatLastSeveralRounds() {
        assertThat(endings())
                .filteredOn(ending -> ending.commands() >= 6)
                .as("combats lasting six commands or more, out of %d", SEEDS)
                .hasSizeGreaterThan(SEEDS / 2);
    }

    /**
     * Two of the three handlers the transition reform rewrote are the card ones. Seeds that only
     * end turns never enter either, and for two thousand seeds none of them did.
     */
    @Test
    void entersBothCardHandlers() {
        assertThat(endings().stream().mapToInt(Ending::cardsPlayedByHand).sum())
                .as("cards played through playCard across %d seeds", SEEDS)
                .isPositive();
        assertThat(endings().stream().mapToInt(Ending::movesPlayedByPattern).sum())
                .as("authored moves played through playAiPattern across %d seeds", SEEDS)
                .isPositive();
    }

    /**
     * A family where one side wins every seed tests one ending. The old one handed the enemies
     * every combat of two thousand, so no seed ever exercised a player victory.
     */
    @Test
    void reachesEveryEndingASeedCanReach() {
        assertThat(winners(PLAYER_TEAM)).as("seeds won by the players").isPositive();
        assertThat(winners(ENEMY_TEAM)).as("seeds won by the enemies").isPositive();
    }

    private static long winners(String teamId) {
        return endings().stream().filter(ending -> teamId.equals(ending.winnerTeamId())).count();
    }

    private static Ending playToEnding(int seed, boolean acts) {
        CombatState initial = CombatScenarios.randomCombat(seed);
        CombatEngine engine = CombatScenarios.engine();

        CombatState state = initial;
        List<CombatEvent> events = new ArrayList<>();
        String actedFor = null;
        int commands = 0;
        while (state.status() == CombatStatus.ACTIVE && commands < COMMAND_LIMIT) {
            CombatCommand command = acts && !state.activeCombatantId().equals(actedFor)
                    ? action(state)
                    : endTurn(state);
            if (!(command instanceof EndTurnCommand)) {
                actedFor = state.activeCombatantId();
            }

            CombatResult result = engine.handle(state, command);
            assertThat(result.accepted())
                    .as("seed %d refused %s at revision %d",
                            seed, command.getClass().getSimpleName(), state.revision())
                    .isTrue();
            events.addAll(result.events());
            state = result.state();
            commands++;
            assertTheActiveCombatantWasGivenItsTurn(seed, state, events);
        }

        assertThat(state.status())
                .as("seed %d never reached an ending in %d commands", seed, COMMAND_LIMIT)
                .isEqualTo(CombatStatus.FINISHED);

        assertTheEndingMatchesTheEvents(seed, initial, state, events);

        return new Ending(
                seed,
                commands,
                state.winnerTeamId(),
                (int) events.stream().filter(CombatEnginePropertyTest::isHandPlay).count(),
                (int) events.stream().filter(CombatEnginePropertyTest::isPatternPlay).count());
    }

    /**
     * Whoever is on turn is whoever the last turn was opened for.
     *
     * <p>Becoming the active combatant is not a title, it is the end of a ceremony: armour
     * cleared, energy restored, statuses run, a hand dealt, {@code TurnStarted} announced. A state
     * that names somebody active whose turn was never opened is legal, throws nothing, and is
     * wrong — the client is told to play with no hand and no energy, one revision after a turn was
     * announced for somebody else. That is what a handler naming a dead combatant as its preferred
     * actor produced, since the state would quietly pick the next living one instead.
     *
     * <p>Checked after every command rather than at the end, because it is a property of each
     * revision and the seed that breaks it is worth naming at the revision that broke it.
     */
    private static void assertTheActiveCombatantWasGivenItsTurn(
            int seed,
            CombatState state,
            List<CombatEvent> events
    ) {
        if (state.status() != CombatStatus.ACTIVE) {
            return;
        }
        String lastOpenedFor = null;
        for (CombatEvent event : events) {
            if (event instanceof TurnStarted started) {
                lastOpenedFor = started.combatantId();
            }
        }
        if (lastOpenedFor == null) {
            // Nothing has been opened yet: the combat is still on the actor it was built with.
            return;
        }
        assertThat(state.activeCombatantId())
                .as("seed %d made %s the active combatant at revision %d, but the last turn "
                        + "opened was %s's", seed, state.activeCombatantId(), state.revision(),
                        lastOpenedFor)
                .isEqualTo(lastOpenedFor);
    }

    /**
     * The independent oracle: where the events say the combat was decided, and what the engine
     * recorded about it.
     *
     * <p>The stream is walked in order, hit points taken from the events that report them, and the
     * combat is decided at the first event leaving fewer than two teams standing. Nothing in this
     * scenario family can kill again after that point within the same resolution — a status only
     * ever strikes its own holder, and the one card damages a single enemy — so the first such
     * event is the whole of the answer.
     */
    private static void assertTheEndingMatchesTheEvents(
            int seed,
            CombatState initial,
            CombatState finished,
            List<CombatEvent> events
    ) {
        List<CombatEnded> reported = events.stream()
                .filter(CombatEnded.class::isInstance)
                .map(CombatEnded.class::cast)
                .toList();
        assertThat(reported)
                .as("seed %d must announce its ending exactly once", seed)
                .hasSize(1);

        CombatEnded ending = reported.getFirst();
        assertThat(ending.revision())
                .as("seed %d announced its ending before its last revision", seed)
                .isEqualTo(finished.revision());
        assertThat(events)
                .as("seed %d wounded somebody after announcing its ending", seed)
                .noneMatch(event -> event.revision() > ending.revision()
                        && (event instanceof HpLost || event instanceof DamageApplied));

        Map<String, String> teams = new HashMap<>();
        Map<String, Integer> hp = new HashMap<>();
        for (CombatantState combatant : initial.combatants()) {
            teams.put(combatant.combatantId(), combatant.teamId());
            hp.put(combatant.combatantId(), combatant.hp());
        }

        Decision decision = null;
        for (CombatEvent event : events) {
            switch (event) {
                case HpLost lost -> hp.put(lost.targetId(), lost.remainingHp());
                case DamageApplied applied -> hp.put(applied.targetId(), applied.remainingHp());
                case HealApplied healed -> hp.put(healed.targetId(), healed.remainingHp());
                default -> {
                }
            }
            Set<String> standing = new HashSet<>();
            hp.forEach((combatantId, remaining) -> {
                if (remaining > 0) {
                    standing.add(teams.get(combatantId));
                }
            });
            if (standing.size() < 2) {
                decision = new Decision(
                        event.revision(),
                        standing.size() == 1 ? standing.iterator().next() : null);
                break;
            }
        }

        assertThat(decision)
                .as("seed %d ended without its events ever deciding it", seed)
                .isNotNull();
        assertThat(ending.revision())
                .as("seed %d announced its ending on revision %d, but its events decided it on "
                        + "revision %d", seed, ending.revision(), decision.revision())
                .isEqualTo(decision.revision());
        assertThat(ending.winnerTeamId())
                .as("seed %d announced %s as the winner, but its events left %s standing",
                        seed, ending.winnerTeamId(), decision.winnerTeamId())
                .isEqualTo(decision.winnerTeamId());
        assertThat(finished.winnerTeamId())
                .as("seed %d recorded a winner its own events do not support", seed)
                .isEqualTo(decision.winnerTeamId());
    }

    /** Where the events say the combat was decided, and who they left standing. */
    private record Decision(long revision, String winnerTeamId) {
    }

    /** What one seed came to, for the questions asked across all of them. */
    private record Ending(
            int seed,
            int commands,
            String winnerTeamId,
            int cardsPlayedByHand,
            int movesPlayedByPattern) {
    }

    /**
     * What the combatant on turn does before ending it: an enemy plays the move it was authored
     * with, anybody else plays a card off its hand. Falls back to ending the turn when there is
     * nothing to play — at the very first command of a combat no hand has been dealt yet.
     */
    private static CombatCommand action(CombatState state) {
        CombatantState actor = state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(state.activeCombatantId()))
                .findFirst()
                .orElseThrow();

        if (actor.controllerType() == ControllerType.AI) {
            return new AiPlayPatternCommand(
                    state.combatId(),
                    state.revision(),
                    actor.combatantId(),
                    CombatScenarios.STRIKE,
                    List.of());
        }
        if (actor.piles().hand().isEmpty() || actor.energy() < 1) {
            return endTurn(state);
        }
        CardInstance card = actor.piles().hand().getFirst();
        return new PlayCardCommand(
                state.combatId(),
                state.revision(),
                actor.combatantId(),
                card.instanceId(),
                List.of());
    }

    private static EndTurnCommand endTurn(CombatState state) {
        return new EndTurnCommand(
                state.combatId(), state.revision(), state.activeCombatantId());
    }

    /**
     * A card played off a hand, as opposed to an authored move: the pattern handler names the
     * instance it never had after the move it played.
     */
    private static boolean isHandPlay(CombatEvent event) {
        return event instanceof CardPlayed played
                && !played.cardInstanceId().startsWith("pattern-");
    }

    private static boolean isPatternPlay(CombatEvent event) {
        return event instanceof CardPlayed played
                && played.cardInstanceId().startsWith("pattern-");
    }
}
