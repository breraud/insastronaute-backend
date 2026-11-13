package fr.insastronaute.webapi.card.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminCardInstanceDto(
        UUID id,
        UUID cardId,
        String cardName,
        String type,
        Integer number,
        String publicId,
        UUID ownerUserId,
        String ownerDisplayName,
        String ownerEmail,
        UUID printRunId,
        Integer printSheetIndex,
        String frontPrintPageId,
        String backPrintPageId,
        Instant assignedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
