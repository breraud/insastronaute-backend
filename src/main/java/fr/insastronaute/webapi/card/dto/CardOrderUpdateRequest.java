package fr.insastronaute.webapi.card.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record CardOrderUpdateRequest(
        @NotEmpty List<UUID> cardIds
) {
}
