package fr.insastronaute.webapi.card.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CardInstanceAssignRequest(
        @NotNull UUID userId
) {
}
