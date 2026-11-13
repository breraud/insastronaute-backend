package fr.insastronaute.webapi.sts.combat.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Records which cards a selection consumed, and what was done with them.
 *
 * <p>The individual {@link CardMoved} events say where each card went; this one says that they
 * moved together as the answer to one selection, which is what a client needs to close its
 * picker without replaying the rule.
 */
public record CardSelectionResolved(
        UUID combatId,
        long revision,
        String combatantId,
        String action,
        List<String> selectedCardInstanceIds
) implements CombatEvent {

    public CardSelectionResolved {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(combatantId, "combatantId");
        requireNonBlank(action, "action");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        selectedCardInstanceIds = List.copyOf(
                Objects.requireNonNull(selectedCardInstanceIds, "selectedCardInstanceIds"));
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
