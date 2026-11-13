package fr.insastronaute.webapi.sts.combat.command;

import java.util.Objects;
import java.util.UUID;
import java.util.List;

/**
 * An enemy playing the move its pattern calls for.
 *
 * @param nextPatternIndex where the pattern goes after this move, worked out by the policy that
 *                         owns the pattern graph, or -1 when the caller has nothing to say and
 *                         the engine should simply step forward
 */
public record AiPlayPatternCommand(
        UUID combatId,
        long expectedRevision,
        String actorId,
        String cardId,
        List<String> targetIds,
        int nextPatternIndex
) implements CombatCommand {

    public AiPlayPatternCommand(
            UUID combatId,
            long expectedRevision,
            String actorId,
            String cardId,
            List<String> targetIds
    ) {
        this(combatId, expectedRevision, actorId, cardId, targetIds, -1);
    }

    public AiPlayPatternCommand {
        Objects.requireNonNull(combatId, "combatId");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must not be negative");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId must not be blank");
        }
        if (cardId == null || cardId.isBlank()) {
            throw new IllegalArgumentException("cardId must not be blank");
        }
        targetIds = targetIds == null ? List.of() : List.copyOf(targetIds);
    }
}
