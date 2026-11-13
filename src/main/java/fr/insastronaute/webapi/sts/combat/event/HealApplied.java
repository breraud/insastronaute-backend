package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

public record HealApplied(
        UUID combatId,
        long revision,
        String sourceId,
        String targetId,
        int requestedHeal,
        int actualHeal,
        int remainingHp
) implements CombatEvent {

    public HealApplied {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(sourceId, "sourceId");
        requireNonBlank(targetId, "targetId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        requireNonNegative(requestedHeal, "requestedHeal");
        requireNonNegative(actualHeal, "actualHeal");
        requireNonNegative(remainingHp, "remainingHp");
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
