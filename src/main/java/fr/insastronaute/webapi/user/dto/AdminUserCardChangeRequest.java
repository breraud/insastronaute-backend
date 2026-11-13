package fr.insastronaute.webapi.user.dto;

import fr.insastronaute.webapi.card.domain.CardCollectionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import java.util.List;

public record AdminUserCardChangeRequest(
        UUID cardId,
        @NotNull CardCollectionType collectionType,
        @Min(1) @Max(100) Integer quantity,
        List<UUID> cardInstanceIds
) {
}
