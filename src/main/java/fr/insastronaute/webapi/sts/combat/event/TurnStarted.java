package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

public record TurnStarted(
        UUID combatId,
        long revision,
        String combatantId,
        long readyAtTick,
        int energy
) implements CombatEvent {

    public TurnStarted {
        Objects.requireNonNull(combatId, "combatId");
        if (combatantId == null || combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId must not be blank");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        if (readyAtTick < 0) {
            throw new IllegalArgumentException("readyAtTick must not be negative");
        }
        if (energy < 0) {
            throw new IllegalArgumentException("energy must not be negative");
        }
    }
}
