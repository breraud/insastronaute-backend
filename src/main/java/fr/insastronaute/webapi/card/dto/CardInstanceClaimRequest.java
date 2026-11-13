package fr.insastronaute.webapi.card.dto;

import jakarta.validation.constraints.NotBlank;

public record CardInstanceClaimRequest(
        @NotBlank String publicId
) {
}
