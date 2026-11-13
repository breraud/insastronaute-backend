package fr.insastronaute.webapi.card.dto;

import java.time.Instant;

public record CardCollectionSettingsDto(
        int physicalFirstAcquisitionMultiplier,
        boolean physicalDuplicatesEnabled,
        int tokensPerPoint,
        Instant updatedAt
) {
}
