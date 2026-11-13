package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
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
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A card that delays or advances its own caster never costs that caster the turn under way, no
 * matter how far it would push the caster past a living combatant — it only owes the caster's
 * <em>next</em> turn a different tick, folded in by {@code CombatEngine#endTurn} once the turn
 * actually ends.
 *
 * <p>The engine previously moved the caster's live timeline entry outright, so a delay large
 * enough could leave the caster active while no longer the first living entry, and
 * {@code CombatState} refused that state — out of the message handler, freezing the player's
 * combat on its revision. The fix here does not hand the turn over instead: the entry a caster is
 * currently taking its turn on stays untouched, and the effect's amount is deferred onto
 * {@link TimelineEntry#pendingSelfDelayTicks()} until the turn genuinely closes.
 */
class CombatEngineDelayedCasterTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000e3");
    private static final String HERO = "hero";
    private static final String ALLY = "ally";
    private static final String ENEMY = "enemy";
    private static final String PLAYERS = "players";
    private static final String ENEMIES = "enemies";
    private static final String SELF_DELAY = "self-delay";
    private static final CardInstance PLAYED_CARD = new CardInstance("played", SELF_DELAY);
    private static final String SELF_FINISH = "self-finish";
    private static final CardInstance FINISHING_CARD = new CardInstance("finish", SELF_FINISH);

    /// The card at the heart of this: it delays whoever plays it, by a percentage of
    /// that combatant's own turn delay. Nine shipped cards do exactly this.
    private static CombatEngine engine(int delayPercent) {
        return engine(EffectType.DELAY_TURN, delayPercent);
    }

    private static CombatEngine engine(EffectType effectType, int percent) {
        return new CombatEngine(
                new CardCatalog("v1", Map.of(SELF_DELAY, new CardDefinition(
                        SELF_DELAY, 1, TargetingMode.SELF,
                        List.of(new EffectDefinition(
                                effectType, percent,
                                true, null, 0, null, null))))),
                new RelicEngine(List.of()));
    }

    private static CombatResult playSelfDelay(CombatState state, int delayPercent) {
        CombatResult result = engine(delayPercent).handle(
                state,
                new PlayCardCommand(
                        COMBAT_ID, state.revision(), HERO,
                        PLAYED_CARD.instanceId(), List.of(HERO)));
        assertThat(result.rejection()).as("rejet inattendu").isNull();
        return result;
    }

    private static CombatResult playSelfAdvance(CombatState state, int advancePercent) {
        CombatResult result = engine(EffectType.ADVANCE_TURN, advancePercent).handle(
                state,
                new PlayCardCommand(
                        COMBAT_ID, state.revision(), HERO,
                        PLAYED_CARD.instanceId(), List.of(HERO)));
        assertThat(result.rejection()).as("rejet inattendu").isNull();
        return result;
    }

    private static CombatResult endTurn(CombatState state, String actorId) {
        CombatResult result = new CombatEngine(new CardCatalog("v1", Map.of()), new RelicEngine(List.of()))
                .handle(state, new EndTurnCommand(COMBAT_ID, state.revision(), actorId));
        assertThat(result.rejection()).as("rejet inattendu").isNull();
        return result;
    }

    /// Hero acting, a living ally close behind on the timeline, one enemy far off. Under the
    /// previous behaviour this closeness is what cost the hero its place; now it changes
    /// nothing about the turn under way.
    private static CombatState allyCloseBehind() {
        return new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(
                        caster(HERO, PLAYERS, ControllerType.HUMAN, 30),
                        drawer(ALLY, PLAYERS, ControllerType.HUMAN, 30),
                        drawer(ENEMY, ENEMIES, ControllerType.AI, 30)),
                List.of(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry(ALLY, 10, 1),
                        new TimelineEntry(ENEMY, 1000, 2)),
                HERO);
    }

    /// A delay that would have pushed the hero behind the nearby ally leaves the hero's turn
    /// untouched: still active, still holding its hand, nothing announced.
    @Test
    void keepsThePlaceAndTheHandWhenTheDelayWouldHavePushedPastSomebodyElse() {
        CombatResult result = playSelfDelay(allyCloseBehind(), 100);

        assertThat(result.state().activeCombatantId()).isEqualTo(HERO);
        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.events()).noneMatch(TurnStarted.class::isInstance);
        assertThat(result.events()).noneMatch(TurnEnded.class::isInstance);

        CombatantState hero = combatant(result.state(), HERO);
        assertThat(hero.piles().hand())
                .extracting(CardInstance::instanceId)
                .containsExactly("kept");
        // The played card itself lands in discard the moment it is played, same as any
        // other card; it is "kept" that this test watches, since only it would be swept up
        // by an end-of-turn discard the caster's turn never actually reached.
        assertThat(hero.piles().discard())
                .extracting(CardInstance::instanceId)
                .containsExactly("played");
    }

    /// Only once the hero actually ends its turn does the delay land: on the next tick this
    /// turn is rescheduled to, not on the one under way.
    @Test
    void deliversTheDeferredDelayOnlyWhenTheTurnActuallyEnds() {
        CombatResult delayed = playSelfDelay(allyCloseBehind(), 100);
        CombatResult ended = endTurn(delayed.state(), HERO);

        // Normal turnDelayTicks is 100 (see caster()); DelayTurn(100%) owes another 100 on
        // top of it, applied once, when the turn that played the card actually closes.
        assertThat(ended.events())
                .filteredOn(TurnEnded.class::isInstance)
                .extracting(event -> (TurnEnded) event)
                .anyMatch(turnEnded -> turnEnded.combatantId().equals(HERO)
                        && turnEnded.previousReadyAtTick() == 0
                        && turnEnded.nextReadyAtTick() == 200);

        // The ally, at tick 10, is who the hero would have lost the turn to under the
        // previous behaviour; now it simply comes up next because its own tick was never
        // disturbed.
        assertThat(ended.state().activeCombatantId()).isEqualTo(ALLY);
        assertThat(ended.events())
                .filteredOn(TurnStarted.class::isInstance)
                .extracting(event -> ((TurnStarted) event).combatantId())
                .contains(ALLY);

        CombatantState ally = combatant(ended.state(), ALLY);
        assertThat(ally.energy()).isEqualTo(ally.baseEnergy());
        assertThat(ally.piles().hand()).isNotEmpty();
    }

    /// The same deferral, at a fraction of a full turn delay, to pin the arithmetic rather
    /// than just the boolean "did it move".
    @Test
    void deliversASmallerDeferredDelayProportionally() {
        CombatResult delayed = playSelfDelay(allyCloseBehind(), 25);
        CombatResult ended = endTurn(delayed.state(), HERO);

        assertThat(ended.events())
                .filteredOn(TurnEnded.class::isInstance)
                .extracting(event -> (TurnEnded) event)
                .anyMatch(turnEnded -> turnEnded.combatantId().equals(HERO)
                        && turnEnded.previousReadyAtTick() == 0
                        && turnEnded.nextReadyAtTick() == 125);
    }

    @Test
    void aFullSelfAdvanceStillSchedulesTheNextTurnInTheFuture() {
        CombatResult advanced = playSelfAdvance(allyCloseBehind(), 100);

        CombatResult ended = endTurn(advanced.state(), HERO);

        assertThat(ended.events())
                .filteredOn(TurnEnded.class::isInstance)
                .extracting(event -> (TurnEnded) event)
                .anyMatch(turnEnded -> turnEnded.combatantId().equals(HERO)
                        && turnEnded.previousReadyAtTick() == 0
                        && turnEnded.nextReadyAtTick() == 1);
    }

    /// Losing the hand costs what losing the hand costs, but not before the turn that lost
    /// it actually closes: playing the card leaves the hand alone, ending the turn discards
    /// it, same as for any other turn.
    @Test
    void discardsTheHandOnlyAtTheRealEndOfTurn() {
        CombatResult delayed = playSelfDelay(allyCloseBehind(), 100);
        CombatResult ended = endTurn(delayed.state(), HERO);

        CombatantState hero = combatant(ended.state(), HERO);
        assertThat(hero.piles().hand()).isEmpty();
        assertThat(hero.piles().discard())
                .extracting(CardInstance::instanceId)
                .contains("kept");
    }

    /// And its end-of-turn statuses tick only there too: a Burn does not fire just because a
    /// self-delay was played, only once the turn it was played in actually ends.
    @Test
    void runsEndOfTurnStatusesOnlyAtTheRealEndOfTurn() {
        CombatResult delayed = playSelfDelay(allyCloseBehindWithBurn(5), 100);
        assertThat(combatant(delayed.state(), HERO).hp()).isEqualTo(30);

        CombatResult ended = endTurn(delayed.state(), HERO);
        assertThat(combatant(ended.state(), HERO).hp()).isEqualTo(25);
    }

    /// The caster killed by its own card keeps its exemption: there is no hook for
    /// dying, so nothing is waiting on that passage.
    @Test
    void stillRunsNoEndOfTurnOnACasterKilledByItsOwnCard() {
        CombatResult result = playSelfFinish(heroAboutToFinishItself());

        assertThat(combatant(result.state(), HERO).hp()).isZero();
        assertThat(result.events()).noneMatch(TurnEnded.class::isInstance);
    }

    /// Counters mirror the same rule: the discard is not observed until the turn that
    /// produces it actually closes.
    @Test
    void countsTheCardsDiscardedOnlyAtTheRealEndOfTurn() {
        CombatResult delayed = playSelfDelay(allyCloseBehind(), 100);
        assertThat(delayed.state().counters().cardsDiscardedThisCombat()).isZero();

        CombatResult ended = endTurn(delayed.state(), HERO);
        assertThat(ended.state().counters().cardsDiscardedThisCombat()).isEqualTo(1);
    }

    // ------------------------------------------------------------------------ fixtures

    /** A hero one hit from death, holding the card that finishes it, an ally standing nearby. */
    private static CombatState heroAboutToFinishItself() {
        return new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(
                        finisher(HERO, PLAYERS, ControllerType.HUMAN, 1),
                        drawer(ALLY, PLAYERS, ControllerType.HUMAN, 30),
                        drawer(ENEMY, ENEMIES, ControllerType.AI, 30)),
                List.of(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry(ALLY, 500, 1),
                        new TimelineEntry(ENEMY, 1000, 2)),
                HERO);
    }

    /// Same as {@link #allyCloseBehind()}, but the hero also carries a Burn: the status
    /// that shows whether the turn it plays the card in really closed.
    private static CombatState allyCloseBehindWithBurn(int burnDamage) {
        return new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(
                        caster(HERO, PLAYERS, ControllerType.HUMAN, 30, List.of(
                                new fr.insastronaute.webapi.sts.combat.model.StatusInstance(
                                        "Burn", 0, burnDamage, null, 0))),
                        drawer(ALLY, PLAYERS, ControllerType.HUMAN, 30),
                        drawer(ENEMY, ENEMIES, ControllerType.AI, 30)),
                List.of(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry(ALLY, 10, 1),
                        new TimelineEntry(ENEMY, 1000, 2)),
                HERO);
    }

    private static CombatResult playSelfFinish(CombatState state) {
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(SELF_FINISH, new CardDefinition(
                        SELF_FINISH, 1, TargetingMode.SELF,
                        List.of(new EffectDefinition(EffectType.DAMAGE, 5))))),
                new RelicEngine(List.of()));
        CombatResult result = engine.handle(
                state,
                new PlayCardCommand(
                        COMBAT_ID, state.revision(), HERO,
                        FINISHING_CARD.instanceId(), List.of(HERO)));
        assertThat(result.rejection()).as("rejet inattendu").isNull();
        return result;
    }

    /** Holds only the card that finishes it off. */
    private static CombatantState finisher(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int hp
    ) {
        return new CombatantState(
                combatantId, teamId, controllerType, 30, hp, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(FINISHING_CARD), List.of(), List.of()));
    }

    /** Holds the card that will delay it, plus one it keeps in hand, so a test can check
     *  whether the end-of-turn discard reaches it. */
    private static CombatantState caster(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int hp
    ) {
        return caster(combatantId, teamId, controllerType, hp, List.of());
    }

    /** Same as above, carrying whatever statuses the test needs it to end its turn with. */
    private static CombatantState caster(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int hp,
            List<fr.insastronaute.webapi.sts.combat.model.StatusInstance> statuses
    ) {
        return new CombatantState(
                combatantId, teamId, controllerType, 30, hp, 0, 3, 3, 100,
                new CardPiles(
                        List.of(),
                        List.of(PLAYED_CARD, new CardInstance("kept", "other-card")),
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
