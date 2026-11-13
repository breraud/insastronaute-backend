package fr.insastronaute.webapi.card.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CardInstanceCreateRequest(
        @NotNull UUID cardId,
        String publicId,
        UUID ownerUserId
) {
}
