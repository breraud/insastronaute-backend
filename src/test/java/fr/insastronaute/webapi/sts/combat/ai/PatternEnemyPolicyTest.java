package fr.insastronaute.webapi.sts.combat.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PatternEnemyPolicyTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000081");

    private final CardCatalog catalog = new CardCatalog("v1", Map.of(
            "expensive", definition("expensive", 4, TargetingMode.SINGLE_ENEMY),
            "strike", definition("strike", 1, TargetingMode.SINGLE_ENEMY),
            "defend", definition("defend", 1, TargetingMode.SELF)));
            
    private final Map<String, List<PatternEnemyPolicy.EnemyMoveNode>> patterns = Map.of(
            "enemy", List.of(
                    new PatternEnemyPolicy.EnemyMoveNode("strike", List.of(1), 1),
                    new PatternEnemyPolicy.EnemyMoveNode("defend", List.of(0), 1),
                    new PatternEnemyPolicy.EnemyMoveNode("expensive", List.of(), 1)
            )
    );
            
    private final PatternEnemyPolicy policy = new PatternEnemyPolicy(catalog, patterns);

    @Test
    void choosesFirstPatternMoveWhenPatternIndexIsZero() {
        CombatState state = state(
                CombatStatus.ACTIVE,
                ControllerType.AI,
                10,
                3,
                0);

        CombatCommand command = policy.nextCommand(state);

        assertThat(command).isEqualTo(new AiPlayPatternCommand(
                COMBAT_ID, 12, "enemy", "strike", List.of("hero"), 1));
    }

    @Test
    void choosesSecondPatternMoveWhenPatternIndexIsOne() {
        CombatState state = state(
                CombatStatus.ACTIVE,
                ControllerType.AI,
                10,
                3,
                1);

        CombatCommand command = policy.nextCommand(state);

        assertThat(command).isEqualTo(new AiPlayPatternCommand(
                COMBAT_ID, 12, "enemy", "defend", List.of("enemy"), 0));
    }

    @Test
    /**
     * A pattern is a ring, as {@code EnemyData.GetActionAt} reads it: an index past the end wraps
     * around it. Restarting at the first move instead left an enemy repeating its opening move
     * for the rest of the fight.
     */
    void wrapsAroundTheRingRatherThanRestartingAtTheFirstMove() {
        CombatState state = state(
                CombatStatus.ACTIVE,
                ControllerType.AI,
                10,
                10,
                5); // two rings past the third move

        CombatCommand command = policy.nextCommand(state);

        assertThat(command).isEqualTo(new AiPlayPatternCommand(
                COMBAT_ID, 12, "enemy", "expensive", List.of("hero"), 0));
    }

    /**
     * An enemy plays its move whatever the move costs.
     *
     * <p>This used to end the turn instead. Energy is the player's economy and every enemy a run
     * builds has none of it, so the question could only ever be answered one way per enemy -- and
     * which way depended on how its moves happened to be written. A move declared inline is a
     * definition of its own priced at zero and always passed; a move naming a card gets that
     * card's player price and always failed. Eight authored enemies write their moves the second
     * way, and none of them ever acted: ending the turn leaves the pattern index alone, so the
     * same unaffordable move came up again every turn for the rest of the fight.
     */
    @Test
    void playsItsMoveEvenWhenItCouldNotAffordTheCardsPrice() {
        CombatState state = state(
                CombatStatus.ACTIVE,
                ControllerType.AI,
                10,
                1, // one energy against a move priced at four
                2); // index points to expensive

        CombatCommand command = policy.nextCommand(state);

        assertThat(command).isInstanceOf(AiPlayPatternCommand.class);
        assertThat(((AiPlayPatternCommand) command).cardId()).isEqualTo("expensive");
    }

    @Test
    void endsTurnIfNoPatternFoundForEnemy() {
        CombatState state = stateMissingEnemyPattern(
                CombatStatus.ACTIVE,
                ControllerType.AI,
                10,
                3,
                0);

        CombatCommand command = policy.nextCommand(state);

        assertThat(command).isEqualTo(new EndTurnCommand(
                COMBAT_ID, 12, "unknown-enemy"));
    }

    @Test
    void rejectsNulls() {
        assertThatNullPointerException().isThrownBy(() -> new PatternEnemyPolicy(null, patterns));
        assertThatNullPointerException().isThrownBy(() -> new PatternEnemyPolicy(catalog, null));
        assertThatNullPointerException().isThrownBy(() -> policy.nextCommand(null));
        assertThatNullPointerException().isThrownBy(() -> policy.predictCommand(null, null));
    }

    @Test
    void nextCommandRequiresActiveCombat() {
        assertThatIllegalArgumentException().isThrownBy(() -> policy.nextCommand(
                state(CombatStatus.FINISHED, ControllerType.AI, 10, 2, 0)));
    }

    @Test
    void nextCommandRequiresLivingAiActor() {
        assertThatIllegalArgumentException().isThrownBy(() -> policy.nextCommand(
                state(CombatStatus.ACTIVE, ControllerType.HUMAN, 10, 2, 0)));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.nextCommand(
                state(CombatStatus.ACTIVE, ControllerType.AI, 0, 2, 0)));
    }

    /**
     * An enemy that skips its turn every turn is what the player sees when its pattern is empty.
     * The turn itself has to stay skippable, so the fight remains playable, but it must not stay
     * silent: the server log is the only place that failure is visible at all.
     */
    @Test
    void reportsAnEnemyThatTakesItsTurnWithoutAnyMove() {
        CombatState state = stateMissingEnemyPattern(
                CombatStatus.ACTIVE, ControllerType.AI, 10, 3, 0);
        ListAppender<ILoggingEvent> log = captureLogOf(PatternEnemyPolicy.class);

        try {
            CombatCommand command = policy.nextCommand(state);

            assertThat(command).isEqualTo(new EndTurnCommand(COMBAT_ID, 12, "unknown-enemy"));
            assertThat(log.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage())
                        .contains("unknown-enemy")
                        .contains(COMBAT_ID.toString());
            });
        } finally {
            releaseLogOf(PatternEnemyPolicy.class, log);
        }
    }

    /**
     * The same wasted turn happening again must not multiply the report: one line per enemy is
     * enough to act on, and a fight is dozens of turns long.
     */
    @Test
    void reportsThatEnemyOnlyOnceHoweverManyTurnsItWastes() {
        CombatState state = stateMissingEnemyPattern(
                CombatStatus.ACTIVE, ControllerType.AI, 10, 3, 0);
        ListAppender<ILoggingEvent> log = captureLogOf(PatternEnemyPolicy.class);

        try {
            policy.nextCommand(state);
            policy.nextCommand(state);
            policy.nextCommand(state);

            assertThat(log.list).hasSize(1);
        } finally {
            releaseLogOf(PatternEnemyPolicy.class, log);
        }
    }

    /**
     * {@code predictCommand} is the intent shown next to the enemy's head, recomputed for every
     * living AI on every state the player is sent. Reporting from there would write a line per
     * enemy per refresh and bury the one line that matters, so the prediction stays silent.
     */
    @Test
    void staysSilentWhilePredictingTheIntentOfAnEnemyWithoutAnyMove() {
        CombatState state = stateMissingEnemyPattern(
                CombatStatus.ACTIVE, ControllerType.AI, 10, 3, 0);
        CombatantState actor = state.combatants().getFirst();
        ListAppender<ILoggingEvent> log = captureLogOf(PatternEnemyPolicy.class);

        try {
            for (int refresh = 0; refresh < 20; refresh++) {
                assertThat(policy.predictCommand(state, actor))
                        .isEqualTo(new EndTurnCommand(COMBAT_ID, 12, "unknown-enemy"));
            }

            assertThat(log.list).isEmpty();
        } finally {
            releaseLogOf(PatternEnemyPolicy.class, log);
        }
    }

    /** An enemy that plays normally says nothing at all. */
    @Test
    void staysSilentForAnEnemyThatHasMoves() {
        CombatState state = state(CombatStatus.ACTIVE, ControllerType.AI, 10, 3, 0);
        ListAppender<ILoggingEvent> log = captureLogOf(PatternEnemyPolicy.class);

        try {
            policy.nextCommand(state);

            assertThat(log.list).isEmpty();
        } finally {
            releaseLogOf(PatternEnemyPolicy.class, log);
        }
    }

    /**
     * The log is the behaviour under test here, so it is read where it is actually written: a
     * Logback appender on the real logger checks the level and the rendered message, which an
     * extracted flag could not.
     */
    private static ListAppender<ILoggingEvent> captureLogOf(Class<?> type) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(type)).addAppender(appender);
        return appender;
    }

    private static void releaseLogOf(Class<?> type, ListAppender<ILoggingEvent> appender) {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(type)).detachAppender(appender);
        appender.stop();
    }

    private static CardDefinition definition(String id, int cost, TargetingMode mode) {
        return new CardDefinition(id, cost, mode, List.of(
                new EffectDefinition(EffectType.DAMAGE, 5, false)));
    }

    private static CombatState state(
            CombatStatus status,
            ControllerType actorController,
            int hp,
            int energy,
            int patternIndex) {
        return new CombatState(
                COMBAT_ID,
                12,
                "v1",
                status,
                null,
                new RandomState(123L),
                List.of(
                        new CombatantState("enemy", "enemy-team", actorController,
                                10, hp, 0, energy, 3, 100,
                                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                                List.of(), List.of(), null, patternIndex),
                        new CombatantState("hero", "hero-team", ControllerType.HUMAN,
                                50, 25, 0, 3, 3, 100,
                                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                                List.of(), List.of(), null, 0)),
                List.of(
                        new TimelineEntry("enemy", 0, 0),
                        new TimelineEntry("hero", 1, 1)),
                status == CombatStatus.ACTIVE ? "enemy" : null);
    }

    private static CombatState stateMissingEnemyPattern(
            CombatStatus status,
            ControllerType actorController,
            int hp,
            int energy,
            int patternIndex) {
        return new CombatState(
                COMBAT_ID,
                12,
                "v1",
                status,
                null,
                new RandomState(123L),
                List.of(
                        new CombatantState("unknown-enemy", "enemy-team", actorController,
                                10, hp, 0, energy, 3, 100,
                                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                                List.of(), List.of(), null, patternIndex),
                        new CombatantState("hero", "hero-team", ControllerType.HUMAN,
                                50, 25, 0, 3, 3, 100,
                                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                                List.of(), List.of(), null, 0)),
                List.of(
                        new TimelineEntry("unknown-enemy", 0, 0),
                        new TimelineEntry("hero", 1, 1)),
                status == CombatStatus.ACTIVE ? "unknown-enemy" : null);
    }
}
