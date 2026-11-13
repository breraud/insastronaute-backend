package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

public record HpLost(
        UUID combatId,
        long revision,
        String sourceId,
        String targetId,
        int requestedLoss,
        int actualLoss,
        int remainingHp
) implements CombatEvent {

    public HpLost {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(sourceId, "sourceId");
        requireNonBlank(targetId, "targetId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        requireNonNegative(requestedLoss, "requestedLoss");
        requireNonNegative(actualLoss, "actualLoss");
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
