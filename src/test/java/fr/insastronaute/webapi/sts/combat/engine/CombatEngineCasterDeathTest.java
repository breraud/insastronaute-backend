package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.CardDrawn;
import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.event.TurnEnded;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A caster its own card kills hands the turn on instead of keeping it.
 *
 * <p>Playing a card and playing an authored enemy move both rebuilt their state around the
 * combatant that came in acting, without asking again whether that combatant was still alive. One
 * card does kill its own caster — {@code ultravibration} damages whoever plays it — and when the
 * caster's team survives the blow the combat is not over, so the handler built an active state
 * whose actor was a corpse. {@code CombatState} refuses that — {@code active combatant must be
 * living} — out of the message handler, which leaves the command unanswered and the player's
 * combat stuck on that revision for good.
 *
 * <p>It takes three combatants to see: with the caster alone on its side the team falls with it,
 * the combat ends, and the handler never gets as far as choosing who acts next.
 *
 * <p>The turn the dead caster was taking stops where it died. Nothing of its end of turn runs —
 * no statuses tick on the body, no end-of-turn relic fires, no hand is discarded — and the turn
 * goes to the first living entry on the timeline, which opens it in full.
 */
class CombatEngineCasterDeathTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000e3");
    private static final String HERO = "hero";
    private static final String ALLY = "ally";
    private static final String ENEMY = "enemy";
    private static final String ENEMY_TWIN = "enemyTwin";
    private static final String PLAYERS = "players";
    private static final String ENEMIES = "enemies";
    private static final String SELF_HARM = "self-harm";
    private static final CardInstance PLAYED_CARD = new CardInstance("played", SELF_HARM);

    // ----------------------------------------------------------------- playing a card

    @Test
    void handsTheTurnToTheAllyWhenTheCardKillsItsOwnCaster() {
        CombatResult result = playSelfHarm(threeWayState(5));

        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.state().winnerTeamId()).isNull();
        assertThat(combatant(result.state(), HERO).hp()).isZero();
        assertThat(result.state().activeCombatantId()).isEqualTo(ALLY);
        assertThat(result.events()).noneMatch(CombatEnded.class::isInstance);
    }

    /**
     * The ally takes a turn like any other: energy back to full, its start-of-turn statuses run,
     * and a hand dealt. A turn handed on without opening would leave it unable to play at all.
     */
    @Test
    void opensTheAllysTurnInFull() {
        CombatResult result = playSelfHarm(threeWayState(5));

        CombatantState ally = combatant(result.state(), ALLY);
        assertThat(ally.energy()).isEqualTo(ally.baseEnergy());
        assertThat(ally.piles().hand()).hasSize(5);
        assertThat(result.events())
                .filteredOn(TurnStarted.class::isInstance)
                .extracting(event -> ((TurnStarted) event).combatantId())
                .containsExactly(ALLY);
        assertThat(result.events()).anyMatch(CardDrawn.class::isInstance);
        assertThat(result.state().counters().turnCount()).isEqualTo(1);
        assertThat(result.state().counters().cardsPlayedThisTurn()).isEmpty();
    }

    /**
     * Nothing of the dead caster's end of turn runs, and nothing announces one: its hand stays
     * where it was and no {@code TurnEnded} is reported for a turn that was cut short rather than
     * closed.
     */
    @Test
    void runsNoEndOfTurnOnTheBody() {
        CombatResult result = playSelfHarm(threeWayState(5));

        CombatantState hero = combatant(result.state(), HERO);
        assertThat(hero.piles().hand()).extracting(CardInstance::instanceId).containsExactly("kept");
        assertThat(hero.piles().discard()).extracting(CardInstance::instanceId)
                .containsExactly(PLAYED_CARD.instanceId());
        assertThat(result.events()).noneMatch(TurnEnded.class::isInstance);
    }

    /** The corpse keeps its place on the timeline; it is stepped over, not struck off. */
    @Test
    void leavesTheDeadCasterOnTheTimelineAndStepsOverIt() {
        CombatResult result = playSelfHarm(threeWayState(5));

        assertThat(result.state().timeline())
                .extracting(TimelineEntry::combatantId)
                .containsExactly(HERO, ALLY, ENEMY);
    }

    /** The far commoner case: the caster's team falls with it and the combat ends as it always did. */
    @Test
    void endsTheCombatWhenTheCasterKillsItselfAndItsTeamFallsWithIt() {
        CombatResult result = playSelfHarm(loneHeroState(5));

        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(result.state().winnerTeamId()).isEqualTo(ENEMIES);
        assertThat(result.state().activeCombatantId()).isNull();
        assertThat(result.state().timeline())
                .extracting(TimelineEntry::combatantId)
                .containsExactly(ENEMY);
        assertThat(result.events())
                .contains(new CombatEnded(COMBAT_ID, result.state().revision(), ENEMIES));
    }

    @Test
    void leavesACardThatSparesItsCasterAlone() {
        CombatResult result = playSelfHarm(threeWayState(30));

        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.state().activeCombatantId()).isEqualTo(HERO);
        assertThat(combatant(result.state(), HERO).hp()).isEqualTo(25);
        assertThat(result.events()).noneMatch(TurnStarted.class::isInstance);
    }

    /**
     * The hand-over is the one the end of a turn uses, so a stun in the way costs its holder the
     * turn here as well: the ally is woken, loses the turn it was too stunned to take, and the
     * turn carries on to the next living entry rather than stopping on the first one found.
     */
    @Test
    void spendsTheStunOfTheCombatantTheDeadCastersTurnWouldHaveGoneTo() {
        CombatResult result = playSelfHarm(stunnedAllyState());

        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.state().activeCombatantId()).isEqualTo(ENEMY);
        assertThat(result.events())
                .filteredOn(TurnStarted.class::isInstance)
                .extracting(event -> ((TurnStarted) event).combatantId())
                .containsExactly(ALLY, ENEMY);
        assertThat(result.events())
                .filteredOn(TurnEnded.class::isInstance)
                .extracting(event -> ((TurnEnded) event).combatantId())
                .containsExactly(ALLY);
        assertThat(result.state().timeline())
                .extracting(TimelineEntry::combatantId)
                .containsExactly(HERO, ENEMY, ALLY);
    }

    // ------------------------------------------------------- playing an authored enemy move

    @Test
    void handsTheTurnToTheSurvivingEnemyWhenTheAuthoredMoveKillsItsOwnCaster() {
        CombatResult result = playSelfHarmPattern(twoEnemyState(5));

        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.state().winnerTeamId()).isNull();
        assertThat(combatant(result.state(), ENEMY).hp()).isZero();
        assertThat(result.state().activeCombatantId()).isEqualTo(ENEMY_TWIN);
        assertThat(result.events())
                .filteredOn(TurnStarted.class::isInstance)
                .extracting(event -> ((TurnStarted) event).combatantId())
                .containsExactly(ENEMY_TWIN);
        assertThat(result.events()).noneMatch(CombatEnded.class::isInstance);
        assertThat(result.events()).noneMatch(TurnEnded.class::isInstance);
    }

    @Test
    void endsTheCombatWhenTheAuthoredMoveKillsTheLastEnemyStanding() {
        CombatResult result = playSelfHarmPattern(loneEnemyState(5));

        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(result.state().winnerTeamId()).isEqualTo(PLAYERS);
        assertThat(result.state().activeCombatantId()).isNull();
        assertThat(result.events())
                .contains(new CombatEnded(COMBAT_ID, result.state().revision(), PLAYERS));
    }

    @Test
    void leavesAnAuthoredMoveThatSparesItsCasterAlone() {
        CombatResult result = playSelfHarmPattern(twoEnemyState(30));

        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.state().activeCombatantId()).isEqualTo(ENEMY);
        assertThat(combatant(result.state(), ENEMY).hp()).isEqualTo(25);
        assertThat(result.events()).noneMatch(TurnStarted.class::isInstance);
    }

    // ------------------------------------------------------------------------ fixtures

    private static CombatEngine engine() {
        return new CombatEngine(
                new CardCatalog("v1", Map.of(SELF_HARM, new CardDefinition(
                        SELF_HARM, 1, TargetingMode.SELF,
                        List.of(new EffectDefinition(EffectType.DAMAGE, 5))))),
                new RelicEngine(List.of()));
    }

    private static CombatResult playSelfHarm(CombatState state) {
        CombatResult result = engine().handle(
                state,
                new PlayCardCommand(
                        COMBAT_ID,
                        state.revision(),
                        HERO,
                        PLAYED_CARD.instanceId(),
                        List.of(HERO)));
        assertThat(result.rejection()).as("rejet inattendu").isNull();
        return result;
    }

    private static CombatResult playSelfHarmPattern(CombatState state) {
        CombatResult result = engine().handle(
                state,
                new AiPlayPatternCommand(
                        COMBAT_ID, state.revision(), ENEMY, SELF_HARM, List.of(ENEMY), 1));
        assertThat(result.rejection()).as("rejet inattendu").isNull();
        return result;
    }

    /** Hero, a living ally beside it, and one enemy: the caster's team survives the caster. */
    private static CombatState threeWayState(int heroHp) {
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(
                        caster(HERO, PLAYERS, ControllerType.HUMAN, heroHp),
                        drawer(ALLY, PLAYERS, ControllerType.HUMAN, 30),
                        drawer(ENEMY, ENEMIES, ControllerType.AI, 30)),
                List.of(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry(ALLY, 500, 1),
                        new TimelineEntry(ENEMY, 1000, 2)),
                HERO);
    }

    /** The ally next in line is stunned, so the turn passes over it to the enemy behind. */
    private static CombatState stunnedAllyState() {
        CombatantState ally = new CombatantState(
                ALLY, PLAYERS, ControllerType.HUMAN, 30, 30, 0, 0, 3, 1000,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(new StatusInstance("Stun", 1, 1, null, 0)));
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(
                        caster(HERO, PLAYERS, ControllerType.HUMAN, 5),
                        ally,
                        drawer(ENEMY, ENEMIES, ControllerType.AI, 30)),
                List.of(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry(ALLY, 500, 1),
                        new TimelineEntry(ENEMY, 700, 2)),
                HERO);
    }

    /** The hero alone against one enemy: killing itself takes its whole team with it. */
    private static CombatState loneHeroState(int heroHp) {
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(
                        caster(HERO, PLAYERS, ControllerType.HUMAN, heroHp),
                        drawer(ENEMY, ENEMIES, ControllerType.AI, 30)),
                List.of(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry(ENEMY, 1000, 1)),
                HERO);
    }

    private static CombatState twoEnemyState(int enemyHp) {
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(
                        caster(ENEMY, ENEMIES, ControllerType.AI, enemyHp),
                        drawer(ENEMY_TWIN, ENEMIES, ControllerType.AI, 30),
                        drawer(HERO, PLAYERS, ControllerType.HUMAN, 30)),
                List.of(
                        new TimelineEntry(ENEMY, 0, 0),
                        new TimelineEntry(ENEMY_TWIN, 500, 1),
                        new TimelineEntry(HERO, 1000, 2)),
                ENEMY);
    }

    private static CombatState loneEnemyState(int enemyHp) {
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(
                        caster(ENEMY, ENEMIES, ControllerType.AI, enemyHp),
                        drawer(HERO, PLAYERS, ControllerType.HUMAN, 30)),
                List.of(
                        new TimelineEntry(ENEMY, 0, 0),
                        new TimelineEntry(HERO, 1000, 1)),
                ENEMY);
    }

    /** Holds the card that will kill it, plus one it keeps in hand to show nothing discards it. */
    private static CombatantState caster(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int hp
    ) {
        return new CombatantState(
                combatantId, teamId, controllerType, 30, hp, 0, 3, 3, 100,
                new CardPiles(
                        List.of(),
                        List.of(PLAYED_CARD, new CardInstance("kept", "other-card")),
                        List.of(),
                        List.of()));
    }

    /** Enough in the draw pile to be dealt a full hand when the turn reaches it. */
    private static CombatantState drawer(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int hp
    ) {
        List<CardInstance> draw = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            draw.add(new CardInstance(combatantId + "-" + index, "other-card"));
        }
        return new CombatantState(
                combatantId, teamId, controllerType, 30, hp, 0, 0, 3, 100,
                new CardPiles(draw, List.of(), List.of(), List.of()));
    }

    private static CombatantState combatant(CombatState state, String combatantId) {
        return state.combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }
}
