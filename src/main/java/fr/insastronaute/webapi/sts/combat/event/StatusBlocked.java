package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Records that a status never landed because something on the target ate it.
 *
 * <p>Without this, a blocked debuff is indistinguishable from one that was never applied, and the
 * player cannot see what their Artifact just spent itself on.
 */
public record StatusBlocked(
        UUID combatId,
        long revision,
        String combatantId,
        String statusType,
        String blockedBy
) implements CombatEvent {

    public StatusBlocked {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(combatantId, "combatantId");
        requireNonBlank(statusType, "statusType");
        requireNonBlank(blockedBy, "blockedBy");
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
