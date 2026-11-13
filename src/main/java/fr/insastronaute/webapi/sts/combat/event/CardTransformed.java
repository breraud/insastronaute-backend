package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Records that a card became a different card without leaving its pile.
 *
 * <p>The instance id is unchanged on purpose, so a client updates the card it is already showing
 * instead of removing one and adding another.
 */
public record CardTransformed(
        UUID combatId,
        long revision,
        String combatantId,
        String cardInstanceId,
        String fromDefinitionId,
        String toDefinitionId,
        String pile
) implements CombatEvent {

    public CardTransformed {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(combatantId, "combatantId");
        requireNonBlank(cardInstanceId, "cardInstanceId");
        requireNonBlank(fromDefinitionId, "fromDefinitionId");
        requireNonBlank(toDefinitionId, "toDefinitionId");
        requireNonBlank(pile, "pile");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
