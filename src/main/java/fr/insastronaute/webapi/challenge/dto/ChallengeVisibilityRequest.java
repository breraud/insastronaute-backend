package fr.insastronaute.webapi.challenge.dto;

import jakarta.validation.constraints.NotNull;

public record ChallengeVisibilityRequest(
        @NotNull(message = "Le masquage est requis.")
        Boolean hidden
) {
}
