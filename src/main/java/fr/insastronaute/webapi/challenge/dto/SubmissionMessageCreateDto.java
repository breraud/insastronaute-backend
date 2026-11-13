package fr.insastronaute.webapi.challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmissionMessageCreateDto(
        @NotBlank
        @Size(max = 2000, message = "Le message doit contenir au plus 2000 caractères.")
        String content
) {
}
