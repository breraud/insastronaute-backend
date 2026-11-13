package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

public record EnergySpent(
        UUID combatId,
        long revision,
        String combatantId,
        int amount,
        int remainingEnergy
) implements CombatEvent {

    public EnergySpent {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(combatantId, "combatantId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        requireNonNegative(amount, "amount");
        requireNonNegative(remainingEnergy, "remainingEnergy");
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
