package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Records that a card was reshaped where it lies: made cheaper, made to hit harder, given an
 * extra effect or a marker.
 *
 * <p>The instance is unchanged, so a client updates the card it is already showing.
 */
public record CardAltered(
        UUID combatId,
        long revision,
        String combatantId,
        String cardInstanceId,
        String alteration,
        String pile
) implements CombatEvent {

    public CardAltered {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(combatantId, "combatantId");
        requireNonBlank(cardInstanceId, "cardInstanceId");
        requireNonBlank(alteration, "alteration");
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
