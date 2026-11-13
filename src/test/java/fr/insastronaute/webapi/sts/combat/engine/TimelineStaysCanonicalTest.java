package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A timeline a temporal effect has rewritten is still a timeline the state will accept.
 *
 * <p>The four handlers that move a turn — delay, advance, cut in, delete — rewrite the tick in
 * place and leave the entry where it sat in the list. Move one past its neighbour and the order
 * the state requires no longer holds, so building the next state threw
 * {@code timeline must use canonical order} out of the message handler: the command was never
 * answered and the combat stopped for good. Only the branch that ends a combat sorted; the branch
 * that carries on did not.
 */
class TimelineStaysCanonicalTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String OTHER_ENEMY = "other-enemy";

    private static final Comparator<TimelineEntry> CANONICAL =
            Comparator.comparingLong(TimelineEntry::readyAtTick)
                    .thenComparingLong(TimelineEntry::sequence)
                    .thenComparing(TimelineEntry::combatantId);

    @Test
    void anEnemyDelayingTheHeroPastItselfDoesNotStopTheCombat() {
        CombatEngine engine = engineWith(delayCard("enemy-move"));

        assertThatCode(() -> engine.handle(
                state(),
                new AiPlayPatternCommand(
                        COMBAT_ID, 7, ENEMY, "enemy-move", List.of(HERO), 1)))
                .doesNotThrowAnyException();
    }

    @Test
    void theTimelineAnEnemyDelayComesBackWithIsInCanonicalOrder() {
        CombatEngine engine = engineWith(delayCard("enemy-move"));

        CombatResult result = engine.handle(
                state(),
                new AiPlayPatternCommand(
                        COMBAT_ID, 7, ENEMY, "enemy-move", List.of(HERO), 1));

        assertThat(result.rejection()).as("rejet inattendu").isNull();
        assertThat(result.state().timeline()).isSortedAccordingTo(CANONICAL);
    }

    @Test
    void aHeroDelayingTheEnemyPastItselfDoesNotStopTheCombat() {
        CombatEngine engine = engineWith(delayCard("repousse"));

        assertThatCode(() -> engine.handle(
                stateWithHeroHolding(new CardInstance("card-1", "repousse")),
                new PlayCardCommand(
                        COMBAT_ID, 7, HERO, "card-1", List.of(ENEMY), List.of())))
                .doesNotThrowAnyException();
    }

    @Test
    void theTimelineAHeroDelayComesBackWithIsInCanonicalOrder() {
        CombatEngine engine = engineWith(delayCard("repousse"));

        CombatResult result = engine.handle(
                stateWithHeroHolding(new CardInstance("card-1", "repousse")),
                new PlayCardCommand(
                        COMBAT_ID, 7, HERO, "card-1", List.of(ENEMY), List.of()));

        assertThat(result.rejection()).as("rejet inattendu").isNull();
        assertThat(result.state().timeline()).isSortedAccordingTo(CANONICAL);
    }

    // A delay of ten times the target's own wait: enough to carry it well past the other entry,
    // which is the whole point — a delay that keeps the order would not have caught anything.
    private static CardDefinition delayCard(String id) {
        return new CardDefinition(id, 0, TargetingMode.SINGLE_ENEMY,
                List.of(new EffectDefinition(
                        EffectType.DELAY_TURN, 1000, false, null, 0, "", null)));
    }

    private static CombatEngine engineWith(CardDefinition card) {
        return new CombatEngine(
                new CardCatalog("v1", Map.of(card.id(), card)),
                new RelicEngine(List.of()));
    }

    private static CombatState state() {
        return stateWith(hero(new CardPiles(List.of(), List.of(), List.of(), List.of())), ENEMY);
    }

    private static CombatState stateWithHeroHolding(CardInstance card) {
        return stateWith(hero(new CardPiles(List.of(), List.of(card), List.of(), List.of())), HERO);
    }

    // Three combatants, as the combat that stopped had: a hero and two enemies. Two is not enough
    // to catch this — delaying whoever already goes last leaves the order alone. It takes a third
    // entry behind the delayed one for the list to end up out of step with itself.
    private static CombatState stateWith(CombatantState hero, String activeCombatantId) {
        CombatantState enemy = enemy(ENEMY);
        CombatantState otherEnemy = enemy(OTHER_ENEMY);
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(hero, enemy, otherEnemy),
                ENEMY.equals(activeCombatantId)
                        ? List.of(
                                new TimelineEntry(ENEMY, 0, 0),
                                new TimelineEntry(HERO, 100, 1),
                                new TimelineEntry(OTHER_ENEMY, 200, 2))
                        : List.of(
                                new TimelineEntry(HERO, 0, 0),
                                new TimelineEntry(ENEMY, 100, 1),
                                new TimelineEntry(OTHER_ENEMY, 200, 2)),
                activeCombatantId);
    }

    private static CombatantState enemy(String combatantId) {
        return new CombatantState(
                combatantId, "enemies", ControllerType.AI, 20, 20, 0, 2, 2, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
    }

    private static CombatantState hero(CardPiles piles) {
        return new CombatantState(
                HERO, "players", ControllerType.HUMAN, 30, 30, 0, 3, 3, 100, piles);
    }
}
