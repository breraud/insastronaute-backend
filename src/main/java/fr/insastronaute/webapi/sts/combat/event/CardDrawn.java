package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

public record CardDrawn(
        UUID combatId,
        long revision,
        String combatantId,
        String cardInstanceId,
        String definitionId,
        int handIndex
) implements CombatEvent {

    public CardDrawn {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(combatantId, "combatantId");
        requireNonBlank(cardInstanceId, "cardInstanceId");
        // The client builds a card it has never seen from the definition this names, and drops
        // the event when it cannot: a created card would simply never appear in hand.
        requireNonBlank(definitionId, "definitionId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        if (handIndex < 0) {
            throw new IllegalArgumentException("handIndex must not be negative");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
