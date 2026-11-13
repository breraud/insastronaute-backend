package fr.insastronaute.webapi.card.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CardCollectionGrantRequest(
        @NotNull UUID userId,
        @Min(1) Integer quantity
) {
}
