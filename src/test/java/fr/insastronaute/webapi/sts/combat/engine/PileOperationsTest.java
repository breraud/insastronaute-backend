package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PileOperationsTest {

    @Test
    void definesTheTopAsIndexZeroAndReturnsAnImmutablePermutation() {
        List<CardInstance> source = new ArrayList<>(List.of(
                card("one"), card("two"), card("three"), card("four")));

        PileOperations.ShuffleResult result =
                PileOperations.shuffle(source, new RandomState(42));

        source.clear();
        assertThat(PileOperations.TOP_INDEX).isZero();
        assertThat(result.cards())
                .extracting(CardInstance::instanceId)
                .containsExactly("three", "one", "four", "two");
        assertThat(result.cards()).containsExactlyInAnyOrder(
                card("one"), card("two"), card("three"), card("four"));
        assertThatThrownBy(() -> result.cards().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void chainedShufflesConsumeExactlyOneRandomStepPerFisherYatesIteration() {
        RandomState initial = new RandomState(42);
        PileOperations.ShuffleResult first =
                PileOperations.shuffle(List.of(card("a"), card("b"), card("c"), card("d")), initial);
        PileOperations.ShuffleResult second =
                PileOperations.shuffle(List.of(card("x"), card("y"), card("z")), first.random());

        RandomState expected = initial;
        for (int bound : List.of(4, 3, 2, 3, 2)) {
            expected = expected.nextInt(bound).next();
        }

        assertThat(first.cards()).containsExactlyInAnyOrder(
                card("a"), card("b"), card("c"), card("d"));
        assertThat(second.cards()).containsExactlyInAnyOrder(
                card("x"), card("y"), card("z"));
        assertThat(second.random()).isEqualTo(expected);
    }

    @Test
    void emptyAndSingleCardPilesDoNotConsumeRandomness() {
        RandomState initial = new RandomState(42);

        assertThat(PileOperations.shuffle(List.of(), initial).random()).isSameAs(initial);
        assertThat(PileOperations.shuffle(List.of(card("only")), initial).random())
                .isSameAs(initial);
    }

    @Test
    void drawsFromIndexZeroAndRecyclesDiscardWithoutProducingEvents() {
        CardInstance inHand = card("in-hand");
        CardInstance first = card("first");
        CardInstance recycledOne = card("recycled-one");
        CardInstance recycledTwo = card("recycled-two");
        RandomState initial = new RandomState(42);
        CardPiles piles = new CardPiles(
                List.of(first),
                List.of(inHand),
                List.of(recycledOne, recycledTwo),
                List.of(card("exhausted")));

        PileOperations.DrawResult result = PileOperations.draw(piles, 3, initial);

        PileOperations.ShuffleResult expectedShuffle =
                PileOperations.shuffle(List.of(recycledOne, recycledTwo), initial);
        assertThat(result.drawn()).containsExactly(
                first, expectedShuffle.cards().get(0), expectedShuffle.cards().get(1));
        assertThat(result.reshuffledOrder()).containsExactlyElementsOf(expectedShuffle.cards());
        assertThat(result.reshuffleAfterDrawnCount()).isEqualTo(1);
        assertThat(result.piles().hand()).containsExactly(
                inHand, first, expectedShuffle.cards().get(0), expectedShuffle.cards().get(1));
        assertThat(result.piles().draw()).isEmpty();
        assertThat(result.piles().discard()).isEmpty();
        assertThat(result.piles().exhaust()).containsExactly(card("exhausted"));
        assertThat(result.random()).isEqualTo(expectedShuffle.random());
        assertThat(piles.hand()).containsExactly(inHand);
        assertThat(piles.draw()).containsExactly(first);
        assertThat(piles.discard()).containsExactly(recycledOne, recycledTwo);
    }

    @Test
    void singletonRecycleDoesNotConsumeRandomnessAndStopsWhenCardsRunOut() {
        RandomState initial = new RandomState(42);
        CardInstance only = card("only");

        PileOperations.DrawResult result = PileOperations.draw(
                new CardPiles(List.of(), List.of(), List.of(only), List.of()),
                5,
                initial);

        assertThat(result.drawn()).containsExactly(only);
        assertThat(result.reshuffledOrder()).containsExactly(only);
        assertThat(result.reshuffleAfterDrawnCount()).isZero();
        assertThat(result.random()).isSameAs(initial);
    }

    @Test
    void rejectsNegativeDrawCounts() {
        CardPiles empty = new CardPiles(List.of(), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> PileOperations.draw(empty, -1, new RandomState(42)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CardInstance card(String id) {
        return new CardInstance(id, "definition");
    }
}
