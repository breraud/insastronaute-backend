package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

public record EnergyGained(
        UUID combatId,
        long revision,
        String combatantId,
        int amount,
        int resultingEnergy
) implements CombatEvent {

    public EnergyGained {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(combatantId, "combatantId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        requireNonNegative(amount, "amount");
        requireNonNegative(resultingEnergy, "resultingEnergy");
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
