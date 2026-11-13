package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Records that a status left a combatant, wholly or in part.
 *
 * @param remainingValue    what the combatant keeps, zero when the status is gone entirely
 * @param remainingDuration how long what is kept still lasts
 * @param reason            what took it off: expiry, a dispel, a steal, a transfer
 */
public record StatusRemoved(
        UUID combatId,
        long revision,
        String combatantId,
        String statusType,
        int removedValue,
        int remainingValue,
        int remainingDuration,
        String reason
) implements CombatEvent {

    public StatusRemoved {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(combatantId, "combatantId");
        requireNonBlank(statusType, "statusType");
        requireNonBlank(reason, "reason");
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
