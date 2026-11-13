package fr.insastronaute.webapi.sts.combat.event;

import java.util.Objects;
import java.util.UUID;

public record CombatEnded(
        UUID combatId,
        long revision,
        String winnerTeamId
) implements CombatEvent {

    public CombatEnded {
        Objects.requireNonNull(combatId, "combatId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        if (winnerTeamId != null && winnerTeamId.isBlank()) {
            throw new IllegalArgumentException("winnerTeamId must not be blank");
        }
    }
}
