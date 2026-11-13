package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

public record TurnEnded(
        UUID combatId,
        long revision,
        String combatantId,
        long previousReadyAtTick,
        long nextReadyAtTick
) implements CombatEvent {

    public TurnEnded {
        Objects.requireNonNull(combatId, "combatId");
        if (combatantId == null || combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId must not be blank");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        if (previousReadyAtTick < 0) {
            throw new IllegalArgumentException("previousReadyAtTick must not be negative");
        }
        if (nextReadyAtTick <= previousReadyAtTick) {
            throw new IllegalArgumentException(
                    "nextReadyAtTick must be greater than previousReadyAtTick");
        }
    }
}
