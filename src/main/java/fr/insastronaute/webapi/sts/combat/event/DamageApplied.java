package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

public record DamageApplied(
        UUID combatId,
        long revision,
        String sourceId,
        String targetId,
        int requestedDamage,
        int armorAbsorbed,
        int hpLost,
        int remainingArmor,
        int remainingHp
) implements CombatEvent {

    public DamageApplied {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(sourceId, "sourceId");
        requireNonBlank(targetId, "targetId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        requireNonNegative(requestedDamage, "requestedDamage");
        requireNonNegative(armorAbsorbed, "armorAbsorbed");
        requireNonNegative(hpLost, "hpLost");
        requireNonNegative(remainingArmor, "remainingArmor");
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
