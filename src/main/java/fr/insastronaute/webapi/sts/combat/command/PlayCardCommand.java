package fr.insastronaute.webapi.sts.combat.command;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PlayCardCommand(
        UUID combatId,
        long expectedRevision,
        String actorId,
        String cardInstanceId,
        List<String> targetIds,
        List<String> selectedCardInstanceIds
) implements CombatCommand {

    public PlayCardCommand {
        Objects.requireNonNull(combatId, "combatId must not be null");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must be non-negative");
        }
        requireNonBlank(actorId, "actorId");
        requireNonBlank(cardInstanceId, "cardInstanceId");
        targetIds = List.copyOf(Objects.requireNonNull(targetIds, "targetIds must not be null"));
        targetIds.forEach(targetId -> requireNonBlank(targetId, "targetId"));
        selectedCardInstanceIds = List.copyOf(Objects.requireNonNull(
                selectedCardInstanceIds, "selectedCardInstanceIds must not be null"));
        selectedCardInstanceIds.forEach(id -> requireNonBlank(id, "selectedCardInstanceId"));
    }

    public PlayCardCommand(
            UUID combatId,
            long expectedRevision,
            String actorId,
            String cardInstanceId,
            List<String> targetIds
    ) {
        this(combatId, expectedRevision, actorId, cardInstanceId, targetIds, List.of());
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
