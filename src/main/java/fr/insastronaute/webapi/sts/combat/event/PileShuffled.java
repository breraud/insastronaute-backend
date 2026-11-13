package fr.insastronaute.webapi.sts.combat.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PileShuffled(
        UUID combatId,
        long revision,
        String combatantId,
        String pile,
        List<String> cardInstanceIds
) implements CombatEvent {

    public PileShuffled {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(combatantId, "combatantId");
        // The client asks which pile was shuffled before it does anything with the order, so an
        // event that does not say leaves it doing nothing at all.
        requireNonBlank(pile, "pile");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        cardInstanceIds = List.copyOf(cardInstanceIds);
        cardInstanceIds.forEach(id -> requireNonBlank(id, "cardInstanceId"));
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
