package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
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
 * The same rule as {@link CombatEngineDelayedCasterTest}, on the authored-move path: a move that
 * delays its own caster never costs that caster the turn under way — it only owes the caster's
 * next turn a different tick, folded in once the turn actually ends.
 *
 * <p>{@code playAiPattern} carries its own copy of the deferral {@code playCard} carries. An
 * authored move reaches {@code DelayTurn} the same way a card does, and {@code
 * stepsThePatternForwardEvenWhenTheCasterLosesTheTurn}/{@code
 * honoursThePatternIndexThePolicyChoseWhenTheCasterLosesTheTurn} keep their old names because the
 * pattern-index bump they pin happens unconditionally, whether or not the caster ever loses
 * anything.
 */
class CombatEngineDelayedAiCasterTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000e5");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String OTHER_ENEMY = "other-enemy";
    private static final String PLAYERS = "players";
    private static final String ENEMIES = "enemies";
    private static final String SELF_DELAY = "self-delay";
    private static final String SELF_FINISH = "self-finish";

    /// A delay large enough to have pushed the enemy behind the player leaves the enemy's
    /// turn untouched: still active, nothing announced.
    @Test
    void keepsThePlaceWhenTheMoveWouldHaveDelayedItsOwnCasterPastSomebodyElse() {
        CombatResult result = playSelfDelay(heroCloseBehind());

        assertThat(result.state().activeCombatantId()).isEqualTo(ENEMY);
        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.events()).noneMatch(TurnStarted.class::isInstance);
        assertThat(result.events()).noneMatch(TurnEnded.class::isInstance);
    }

    /// Only once the enemy actually ends its turn does the deferred delay land, handing the
    /// turn to whoever it would have lost its place to under the old behaviour.
    @Test
    void deliversTheDeferredDelayOnlyWhenTheTurnActuallyEnds() {
        CombatResult delayed = playSelfDelay(heroCloseBehind());
        CombatResult ended = endTurn(delayed.state(), ENEMY);

        assertThat(ended.events())
                .filteredOn(TurnEnded.class::isInstance)
                .extracting(event -> (TurnEnded) event)
                .anyMatch(turnEnded -> turnEnded.combatantId().equals(ENEMY)
                        && turnEnded.previousReadyAtTick() == 0
                        && turnEnded.nextReadyAtTick() == 200);

        assertThat(ended.state().activeCombatantId()).isEqualTo(HERO);
        assertThat(ended.events())
                .filteredOn(TurnStarted.class::isInstance)
                .extracting(event -> ((TurnStarted) event).combatantId())
                .contains(HERO);
    }

    /// A caster that is never delayed at all keeps playing, same as before: the common case,
    /// and the best detector of a rule applied too widely.
    @Test
    void keepsTheTurnWhenThereIsNoDelayAtAll() {
        CombatResult result = playSelfDelay(heroFarBehind());

        assertThat(result.state().activeCombatantId()).isEqualTo(ENEMY);
    }

    /// The hand is not discarded just because a self-delaying move was played -- only once
    /// the turn it was played in actually ends.
    @Test
    void discardsTheHandOnlyAtTheRealEndOfTurn() {
        CombatResult delayed = playSelfDelay(heroCloseBehind());
        assertThat(combatant(delayed.state(), ENEMY).piles().hand()).isNotEmpty();

        CombatResult ended = endTurn(delayed.state(), ENEMY);
        CombatantState enemy = combatant(ended.state(), ENEMY);
        assertThat(enemy.piles().hand()).isEmpty();
        assertThat(enemy.piles().discard())
                .extracting(CardInstance::instanceId)
                .contains("enemy-held");
    }

    /// And its end-of-turn statuses tick only there too: a Burn does not fire just because a
    /// self-delaying move was played.
    @Test
    void runsEndOfTurnStatusesOnlyAtTheRealEndOfTurn() {
        CombatResult delayed = playSelfDelay(heroCloseBehindWithBurn(5));
        assertThat(combatant(delayed.state(), ENEMY).hp()).isEqualTo(30);

        CombatResult ended = endTurn(delayed.state(), ENEMY);
        assertThat(combatant(ended.state(), ENEMY).hp()).isEqualTo(25);
    }

    /// The caster killed by its own move keeps its exemption: there is no hook for dying, so
    /// nothing is waiting on that passage. An ally keeps the fight going.
    @Test
    void runsNoEndOfTurnOnACasterKilledByItsOwnMove() {
        CombatResult result = playSelfFinish(enemyAboutToFinishItself());

        assertThat(combatant(result.state(), ENEMY).hp()).isZero();
        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.events()).noneMatch(TurnEnded.class::isInstance);
    }

    /// Counters mirror the same rule: the discard is not observed until the turn that
    /// produces it actually closes.
    @Test
    void countsTheCardsDiscardedOnlyAtTheRealEndOfTurn() {
        CombatResult delayed = playSelfDelay(heroCloseBehind());
        assertThat(delayed.state().counters().cardsDiscardedThisCombat()).isZero();

        CombatResult ended = endTurn(delayed.state(), ENEMY);
        assertThat(ended.state().counters().cardsDiscardedThisCombat()).isEqualTo(1);
    }

    /// Losing the hand does not cost the pattern its step: the graph moves on, or the enemy
    /// would answer the same move for ever. Unaffected by the deferral, since the bump runs
    /// unconditionally as part of resolving the move.
    @Test
    void stepsThePatternForwardEvenWhenTheCasterLosesTheTurn() {
        CombatResult result = playSelfDelay(heroCloseBehind());

        assertThat(combatant(result.state(), ENEMY).patternIndex()).isEqualTo(1);
    }

    /// And where the policy names the next move, that is the one taken, whether or not the
    /// caster keeps its place.
    @Test
    void honoursThePatternIndexThePolicyChoseWhenTheCasterLosesTheTurn() {
        CombatResult result = engine().handle(
                heroCloseBehind(),
                new AiPlayPatternCommand(
                        COMBAT_ID, 7, ENEMY, SELF_DELAY, List.of(ENEMY), 4));
        assertThat(result.rejection()).as("rejet inattendu").isNull();

        assertThat(combatant(result.state(), ENEMY).patternIndex()).isEqualTo(4);
        assertThat(result.state().activeCombatantId()).isEqualTo(ENEMY);
    }

    // ------------------------------------------------------------------------ fixtures

    /// Enemy acting, the player ten ticks behind it: close enough that, under the previous
    /// behaviour, one full turn delay would have cost the enemy its place.
    private static CombatState heroCloseBehind() {
        return state(10, List.of());
    }

    /// Same, with the enemy carrying a Burn: the status that shows whether the turn it
    /// plays the move in really closed.
    private static CombatState heroCloseBehindWithBurn(int burnDamage) {
        return state(10, List.of(new StatusInstance("Burn", 0, burnDamage, null, 0)));
    }

    /// The player far enough ahead in ticks that the delay would not have cost the enemy
    /// its place even under the previous behaviour.
    private static CombatState heroFarBehind() {
        return state(1000, List.of());
    }

    private static CombatState state(long heroReadyAtTick, List<StatusInstance> enemyStatuses) {
        return new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(
                        caster(ENEMY, ENEMIES, ControllerType.AI, 30, enemyStatuses),
                        drawer(HERO, PLAYERS, ControllerType.HUMAN, 30)),
                List.of(
                        new TimelineEntry(ENEMY, 0, 0),
                        new TimelineEntry(HERO, heroReadyAtTick, 1)),
                ENEMY);
    }

    /** An enemy one hit from its own move, with an ally that keeps the fight going. */
    private static CombatState enemyAboutToFinishItself() {
        return new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(
                        caster(ENEMY, ENEMIES, ControllerType.AI, 1, List.of()),
                        drawer(OTHER_ENEMY, ENEMIES, ControllerType.AI, 30),
                        drawer(HERO, PLAYERS, ControllerType.HUMAN, 30)),
                List.of(
                        new TimelineEntry(ENEMY, 0, 0),
                        new TimelineEntry(OTHER_ENEMY, 500, 1),
                        new TimelineEntry(HERO, 1000, 2)),
                ENEMY);
    }

    private static CombatEngine engine() {
        return new CombatEngine(
                new CardCatalog("v1", Map.of(
                        SELF_DELAY, new CardDefinition(
                                SELF_DELAY, 1, TargetingMode.SELF,
                                List.of(new EffectDefinition(
                                        EffectType.DELAY_TURN, 100, true, null, 0, null, null))),
                        SELF_FINISH, new CardDefinition(
                                SELF_FINISH, 1, TargetingMode.SELF,
                                List.of(new EffectDefinition(EffectType.DAMAGE, 5))))),
                new RelicEngine(List.of()));
    }

    private static CombatResult playSelfDelay(CombatState state) {
        return play(state, SELF_DELAY);
    }

    private static CombatResult playSelfFinish(CombatState state) {
        return play(state, SELF_FINISH);
    }

    private static CombatResult play(CombatState state, String cardId) {
        CombatResult result = engine().handle(
                state,
                new AiPlayPatternCommand(
                        COMBAT_ID, state.revision(), ENEMY, cardId, List.of(ENEMY)));
        assertThat(result.rejection()).as("rejet inattendu").isNull();
        return result;
    }

    private static CombatResult endTurn(CombatState state, String actorId) {
        CombatResult result = engine()
                .handle(state, new EndTurnCommand(COMBAT_ID, state.revision(), actorId));
        assertThat(result.rejection()).as("rejet inattendu").isNull();
        return result;
    }

    /** Holds one card in hand, so a test can check whether the end-of-turn discard reaches
     *  it — an authored move is not played out of hand, so nothing else puts one there. */
    private static CombatantState caster(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int hp,
            List<StatusInstance> statuses
    ) {
        return new CombatantState(
                combatantId, teamId, controllerType, 30, hp, 0, 3, 3, 100,
                new CardPiles(
                        List.of(),
                        List.of(new CardInstance(combatantId + "-held", "other-card")),
                        List.of(),
                        List.of()),
                statuses);
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

