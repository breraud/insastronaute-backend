package fr.insastronaute.webapi.card.dto;

import java.time.Instant;
import java.util.UUID;

public record CardInstanceDto(
        UUID id,
        UUID cardId,
        String cardName,
        String publicId,
        UUID ownerUserId,
        String ownerDisplayName,
        String ownerEmail,
        Instant assignedAt,
        Instant createdAt,
        Instant updatedAt,
        // Vrai quand la carte vient du code goofy : le front joue alors une autre
        // animation d'obtention. Faux partout ailleurs.
        boolean goofy
) {
}
