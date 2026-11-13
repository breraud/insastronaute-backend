package fr.insastronaute.webapi.challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChallengeCategoryRequest(
        @NotBlank
        @Size(max = 50, message = "Le nom de catégorie doit contenir au plus 50 caractères.")
        String name
) {
}
