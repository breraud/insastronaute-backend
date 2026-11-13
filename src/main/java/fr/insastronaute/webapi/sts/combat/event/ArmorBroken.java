package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

public record ArmorBroken(
        UUID combatId,
        long revision,
        String sourceId,
        String targetId,
        int armorLost
) implements CombatEvent {

    public ArmorBroken {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(sourceId, "sourceId");
        requireNonBlank(targetId, "targetId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        if (armorLost < 0) {
            throw new IllegalArgumentException("armorLost must not be negative");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
