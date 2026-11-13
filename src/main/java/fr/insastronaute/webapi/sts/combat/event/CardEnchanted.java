package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Records that a card gained levels of an enchantment while staying in its pile.
 *
 * <p>{@code levels} is what this enchant added and {@code totalLevel} what the card now holds, so
 * a client can show the gain without tracking the card's whole history.
 */
public record CardEnchanted(
        UUID combatId,
        long revision,
        String combatantId,
        String cardInstanceId,
        String enchantmentId,
        int levels,
        int totalLevel,
        String pile
) implements CombatEvent {

    public CardEnchanted {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(combatantId, "combatantId");
        requireNonBlank(cardInstanceId, "cardInstanceId");
        requireNonBlank(enchantmentId, "enchantmentId");
        requireNonBlank(pile, "pile");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        if (levels <= 0) {
            throw new IllegalArgumentException("levels must be positive");
        }
        if (totalLevel < levels) {
            throw new IllegalArgumentException("totalLevel must include the levels just added");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
