package fr.insastronaute.webapi.deck.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DropTierTemplateRequest(
        @NotBlank(message = "Le nom du modèle est requis.")
        @Size(max = 120, message = "Le nom du modèle doit contenir au plus 120 caractères.")
        String name,

        @Positive(message = "Le nombre de cartes par tirage doit être supérieur à 0.")
        Integer cardsPerPull,

        @NotEmpty(message = "Un modèle doit contenir au moins un palier.")
        @Valid
        List<DropTierCreateRequest> dropTiers
) {
}
