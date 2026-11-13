package fr.insastronaute.webapi.sts.combat.engine.relic;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.EPRelicHandler;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * EP draws a card at the start of every turn, and a draw off an empty pile reshuffles the
 * discard -- which spends randomness.
 *
 * <p>The hook could not hand that randomness back: it returned a combatant and nothing else, so
 * the combat went on holding the seed exactly as it stood before the reshuffle. Nothing looked
 * broken, because a draw off a non-empty pile spends nothing at all and the reshuffle it did
 * perform was correct. It was the <em>next</em> reshuffle that paid: starting from the same state,
 * it dealt the same order.
 */
class EPRelicRandomTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000417");

    @Test
    void handsBackTheRandomnessItsReshuffleSpent() {
        RandomState before = new RandomState(12345L);

        var result = new EPRelicHandler()
                .onTurnStart(state(before), heroWithEmptyDrawPile(), before, new ArrayList<>());

        assertThat(result.random())
                .as("the draw had to reshuffle the discard, and that costs randomness")
                .isNotEqualTo(before);
        assertThat(result.combatant().piles().hand())
                .as("and it really did draw")
                .hasSize(1);
    }

    /**
     * The consequence, stated as the player would meet it: two reshuffles in a row must not deal
     * the same order. Feeding the second the randomness the first gave back is what separates them.
     */
    @Test
    void twoSuccessiveReshufflesDoNotDealTheSameOrder() {
        EPRelicHandler relic = new EPRelicHandler();
        RandomState random = new RandomState(999L);

        var first = relic.onTurnStart(
                state(random), heroWithEmptyDrawPile(), random, new ArrayList<>());
        var second = relic.onTurnStart(
                state(first.random()), heroWithEmptyDrawPile(), first.random(), new ArrayList<>());

        assertThat(second.combatant().piles().draw())
                .as("a second reshuffle fed the advanced state deals a different order")
                .isNotEqualTo(first.combatant().piles().draw());
    }

    /** A relic that spends nothing hands back exactly what it was given. */
    @Test
    void spendsNothingWhenTheDrawPileIsNotEmpty() {
        RandomState before = new RandomState(7L);
        CombatantState hero = hero(deck(6), List.of());

        var result = new EPRelicHandler()
                .onTurnStart(state(before), hero, before, new ArrayList<>());

        assertThat(result.random()).isEqualTo(before);
    }

    // ------------------------------------------------------------------------ fixtures

    private static CombatantState heroWithEmptyDrawPile() {
        return hero(List.of(), deck(8));
    }

    private static CombatantState hero(List<CardInstance> draw, List<CardInstance> discard) {
        return new CombatantState(
                "hero", "players", ControllerType.HUMAN, 50, 50, 0, 3, 3, 10,
                new CardPiles(draw, List.of(), discard, List.of()));
    }

    private static List<CardInstance> deck(int size) {
        List<CardInstance> cards = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            cards.add(new CardInstance("card-" + index, "strike"));
        }
        return cards;
    }

    private static CombatState state(RandomState random) {
        CombatantState hero = heroWithEmptyDrawPile();
        CombatantState enemy = new CombatantState(
                "enemy", "enemies", ControllerType.AI, 20, 20, 0, 0, 0, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                COMBAT_ID, 1L, "v1", CombatStatus.ACTIVE, null, random,
                List.of(hero, enemy),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy", 5, 1)),
                "hero");
    }
}
