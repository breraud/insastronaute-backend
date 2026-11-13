package fr.insastronaute.webapi.treasurehunt.dto;

import jakarta.validation.constraints.NotNull;

public record TreasureHuntVisibilityRequest(
        @NotNull(message = "La visibilité est requise.")
        Boolean visible
) {
}
