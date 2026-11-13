package fr.insastronaute.webapi.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Le nom de catégorie est requis.")
        @Size(max = 120, message = "Le nom de catégorie doit contenir au plus 120 caractères.")
        String name,
        @Size(max = 140, message = "Le slug de catégorie doit contenir au plus 140 caractères.")
        String slug
) {
}
