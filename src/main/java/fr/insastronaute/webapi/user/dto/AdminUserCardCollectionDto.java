package fr.insastronaute.webapi.user.dto;

import fr.insastronaute.webapi.card.domain.CardCollectionType;

import java.time.Instant;
import java.util.UUID;

public record AdminUserCardCollectionDto(
        UUID collectionId,
        CardCollectionType collectionType,
        UUID cardId,
        String cardName,
        String type,
        Integer number,
        int quantity,
        Instant firstDiscoveredAt,
        Instant updatedAt
) {
}
