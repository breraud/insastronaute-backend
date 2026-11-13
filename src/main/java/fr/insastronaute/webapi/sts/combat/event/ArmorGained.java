package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

public record ArmorGained(
        UUID combatId,
        long revision,
        String sourceId,
        String targetId,
        int requestedArmor,
        int appliedArmor,
        int resultingArmor
) implements CombatEvent {

    public ArmorGained {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(sourceId, "sourceId");
        requireNonBlank(targetId, "targetId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        requireNonNegative(requestedArmor, "requestedArmor");
        requireNonNegative(appliedArmor, "appliedArmor");
        requireNonNegative(resultingArmor, "resultingArmor");
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
