package fr.insastronaute.webapi.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EventCategoryRequest(
        @NotBlank(message = "Le nom de la catégorie est requis.")
        @Size(max = 120, message = "Le nom de la catégorie ne peut pas dépasser 120 caractères.")
        String name
) {
}
