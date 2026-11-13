package fr.insastronaute.webapi.user.dto;

import fr.insastronaute.webapi.card.domain.CardCollectionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AdminUserCardRemovalRequest(
        @NotNull CardCollectionType collectionType,
        UUID cardId,
        @Min(1) @Max(100) Integer quantity,
        List<UUID> cardInstanceIds
) {
}
