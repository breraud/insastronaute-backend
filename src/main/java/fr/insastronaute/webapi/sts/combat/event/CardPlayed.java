package fr.insastronaute.webapi.sts.combat.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CardPlayed(
        UUID combatId,
        long revision,
        String actorId,
        String cardInstanceId,
        String definitionId,
        List<String> targetIds
) implements CombatEvent {

    public CardPlayed {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(actorId, "actorId");
        requireNonBlank(cardInstanceId, "cardInstanceId");
        requireNonBlank(definitionId, "definitionId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        targetIds = List.copyOf(Objects.requireNonNull(targetIds, "targetIds"));
        targetIds.forEach(targetId -> requireNonBlank(targetId, "targetId"));
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
