package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Records that a combatant now carries a status it did not carry before, or more of one it did.
 *
 * @param value      what the status holds after the application
 * @param duration   how long it now lasts, negative meaning it does not expire on its own
 * @param stacked    true when it merged into an instance the combatant already had
 */
public record StatusApplied(
        UUID combatId,
        long revision,
        String sourceId,
        String combatantId,
        String statusType,
        int value,
        int duration,
        boolean stacked
) implements CombatEvent {

    public StatusApplied {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(combatantId, "combatantId");
        requireNonBlank(statusType, "statusType");
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
