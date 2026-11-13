package fr.insastronaute.webapi.sts.combat.event;

import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import java.util.Objects;
import java.util.UUID;

/**
 * A card changing pile.
 *
 * <p>{@code instance} is the card itself, and it is carried only when the card is not simply its
 * definition: a conjured copy with its own name, family and price, or one the fight has marked.
 * A move announces a card the client may be seeing for the first time -- nothing conjured has
 * ever been in a pile it could look in -- and a {@code definitionId} alone told it only what the
 * card was made from, never what this copy of it is. It arrived wearing the wrong name.
 */
public record CardMoved(
        UUID combatId,
        long revision,
        String combatantId,
        String cardInstanceId,
        String definitionId,
        String fromPile,
        String toPile,
        int destinationIndex,
        CardInstance instance
) implements CombatEvent {

    /** A move of a card that is nothing more than its definition, which is most of them. */
    public CardMoved(
            UUID combatId,
            long revision,
            String combatantId,
            String cardInstanceId,
            String definitionId,
            String fromPile,
            String toPile,
            int destinationIndex
    ) {
        this(combatId, revision, combatantId, cardInstanceId, definitionId, fromPile, toPile,
                destinationIndex, null);
    }

    public CardMoved {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(combatantId, "combatantId");
        requireNonBlank(cardInstanceId, "cardInstanceId");
        requireNonBlank(definitionId, "definitionId");
        requireNonBlank(fromPile, "fromPile");
        requireNonBlank(toPile, "toPile");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        if (destinationIndex < 0) {
            throw new IllegalArgumentException("destinationIndex must not be negative");
        }
        // A plain card says nothing the definition does not already say, so it is left off.
        instance = instance == null || instance.isPlain() ? null : instance;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
