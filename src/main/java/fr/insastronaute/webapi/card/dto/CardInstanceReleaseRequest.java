package fr.insastronaute.webapi.card.dto;

import jakarta.validation.constraints.NotBlank;

public record CardInstanceReleaseRequest(
        @NotBlank String publicId
) {
}
