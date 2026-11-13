package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure pile algorithms. The top card of every pile is always at index {@value #TOP_INDEX}.
 */
public final class PileOperations {

    public static final int TOP_INDEX = 0;

    /**
     * La carte qu'on reçoit quand il n'y a plus rien à piocher (DeckManager.Draw : « Désespoir »).
     */
    public static final String DESPAIR_CARD_ID = "desespoir";

    /**
     * Au-delà, une main vide n'est plus une main vide : Unity ne console que le joueur qui n'a
     * plus rien à jouer, pas celui qui garde encore de quoi faire (DeckManager.cs:47).
     */
    private static final int DESPAIR_HAND_SIZE = 1;

    private PileOperations() {
    }

    /** Où une carte glissée dans une pile a atterri, et le flux tel que le tirage l'a laissé. */
    public record Insertion(int index, RandomState random) {

        public Insertion {
            Objects.requireNonNull(random, "random");
        }
    }

    /**
     * Glisse une carte n'importe où dans une pile, plutôt qu'en dessous.
     *
     * <p>Une carte ajoutée à la pioche ou à la défausse arrivait toujours tout au fond : une
     * carte donnée en cours de combat ne se voyait donc jamais avant que la pile entière n'ait
     * défilé, ce qui revient à ne pas l'avoir donnée. Le rang est tiré du flux aléatoire du
     * combat, pour qu'un combat rejoué la range au même endroit.
     */
    public static Insertion insertAtRandom(
            List<CardInstance> pile, CardInstance card, RandomState random) {
        Objects.requireNonNull(pile, "pile");
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(random, "random");
        RandomState.RandomStep step = random.nextInt(pile.size() + 1);
        pile.add(step.value(), card);
        return new Insertion(step.value(), step.next());
    }

    public static ShuffleResult shuffle(List<CardInstance> source, RandomState initialRandom) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(initialRandom, "initialRandom");
        List<CardInstance> cards = new ArrayList<>(source);
        RandomState random = initialRandom;

        for (int index = cards.size() - 1; index >= 1; index--) {
            RandomState.RandomStep step = random.nextInt(index + 1);
            CardInstance selected = cards.get(step.value());
            cards.set(step.value(), cards.get(index));
            cards.set(index, selected);
            random = step.next();
        }
        return new ShuffleResult(cards, random);
    }

    /**
     * Draws cards from the top of the draw pile, recycling the discard pile at the boundary.
     *
     * <p>This transition is deliberately event-free. Callers use {@link DrawResult#drawn()} and
     * {@link DrawResult#reshuffledOrder()} to describe the transition in their own event stream.
     */
    public static DrawResult draw(CardPiles initialPiles, int count, RandomState initialRandom) {
        return draw(initialPiles, count, initialRandom, null);
    }

    /**
     * The same draw, able to console a player who has nothing left to draw.
     *
     * @param despairCardId the card to hand over when both piles are empty and the hand is down
     *                      to nothing, or null when the caller cannot promise the combat's
     *                      catalog holds it -- an instance of a card nothing can resolve is worse
     *                      than an empty hand: the AI policies and the card handlers all expect
     *                      every held card to have a definition, and throw when one does not.
     */
    public static DrawResult draw(
            CardPiles initialPiles, int count, RandomState initialRandom, String despairCardId) {
        Objects.requireNonNull(initialPiles, "initialPiles");
        Objects.requireNonNull(initialRandom, "initialRandom");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }

        List<CardInstance> draw = new ArrayList<>(initialPiles.draw());
        List<CardInstance> hand = new ArrayList<>(initialPiles.hand());
        List<CardInstance> discard = new ArrayList<>(initialPiles.discard());
        List<CardInstance> drawn = new ArrayList<>();
        List<CardInstance> reshuffledOrder = List.of();
        int reshuffleAfterDrawnCount = -1;
        RandomState random = initialRandom;

        for (int drawnCount = 0; drawnCount < count; drawnCount++) {
            if (draw.isEmpty()) {
                if (discard.isEmpty()) {
                    // Plus rien à piocher, et une main qui n'a plus rien à jouer : le jeu donne
                    // un Désespoir plutôt que de laisser le joueur devant une main vide
                    // (DeckManager.Draw). Un seul, et la pioche s'arrête là.
                    if (despairCardId != null && hand.size() <= DESPAIR_HAND_SIZE) {
                        random = random.advance();
                        CardInstance despair = new CardInstance(
                                GeneratedCardId.fromRandom(random), despairCardId);
                        hand.add(despair);
                        drawn.add(despair);
                    }
                    break;
                }
                reshuffleAfterDrawnCount = drawn.size();
                ShuffleResult shuffle = shuffle(discard, random);
                reshuffledOrder = shuffle.cards();
                draw.addAll(shuffle.cards());
                discard.clear();
                random = shuffle.random();
            }

            CardInstance card = draw.remove(TOP_INDEX);
            hand.add(card);
            drawn.add(card);
        }

        return new DrawResult(
                new CardPiles(draw, hand, discard, initialPiles.exhaust()),
                drawn,
                reshuffledOrder,
                reshuffleAfterDrawnCount,
                random);
    }

    public record ShuffleResult(List<CardInstance> cards, RandomState random) {

        public ShuffleResult {
            cards = List.copyOf(Objects.requireNonNull(cards, "cards"));
            Objects.requireNonNull(random, "random");
        }
    }

    public record DrawResult(
            CardPiles piles,
            List<CardInstance> drawn,
            List<CardInstance> reshuffledOrder,
            int reshuffleAfterDrawnCount,
            RandomState random
    ) {

        public DrawResult {
            Objects.requireNonNull(piles, "piles");
            drawn = List.copyOf(Objects.requireNonNull(drawn, "drawn"));
            reshuffledOrder =
                    List.copyOf(Objects.requireNonNull(reshuffledOrder, "reshuffledOrder"));
            if (reshuffleAfterDrawnCount < -1 || reshuffleAfterDrawnCount > drawn.size()) {
                throw new IllegalArgumentException("invalid reshuffle draw boundary");
            }
            Objects.requireNonNull(random, "random");
        }
    }
}
