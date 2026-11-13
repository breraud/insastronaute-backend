package fr.insastronaute.webapi.sts.combat.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Records that several cards became one.
 *
 * <p>The merged card is new rather than one of the originals wearing the others, so a client has
 * to remove all the sources and add the result rather than update one of them in place.
 */
public record CardMerged(
        UUID combatId,
        long revision,
        String combatantId,
        List<String> mergedCardInstanceIds,
        String resultingCardInstanceId,
        String resultingDefinitionId,
        String resultingDisplayName
) implements CombatEvent {

    public CardMerged {
        Objects.requireNonNull(combatId, "combatId");
        requireNonBlank(combatantId, "combatantId");
        requireNonBlank(resultingCardInstanceId, "resultingCardInstanceId");
        requireNonBlank(resultingDefinitionId, "resultingDefinitionId");
        mergedCardInstanceIds = List.copyOf(
                Objects.requireNonNull(mergedCardInstanceIds, "mergedCardInstanceIds"));
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
