package fr.insastronaute.webapi.sts.combat.command;

import java.util.Objects;
import java.util.UUID;

public record EndTurnCommand(
        UUID combatId,
        long expectedRevision,
        String actorId
) implements CombatCommand {

    public EndTurnCommand {
        Objects.requireNonNull(combatId, "combatId must not be null");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must be non-negative");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId must not be blank");
        }
    }
}
