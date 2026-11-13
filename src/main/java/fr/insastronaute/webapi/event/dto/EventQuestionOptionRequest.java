package fr.insastronaute.webapi.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record EventQuestionOptionRequest(
        /** Present when editing an existing option: keeping the id keeps the answers. */
        UUID id,

        @NotBlank(message = "Le libellé de l'option est requis.")
        @Size(max = 200, message = "Le libellé d'option ne peut pas dépasser 200 caractères.")
        String label
) {
}
