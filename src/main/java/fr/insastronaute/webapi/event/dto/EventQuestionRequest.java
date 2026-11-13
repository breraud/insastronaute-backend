package fr.insastronaute.webapi.event.dto;

import fr.insastronaute.webapi.event.domain.EventQuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record EventQuestionRequest(
        /** Present when editing an existing question: keeping the id keeps the answers. */
        UUID id,

        @NotBlank(message = "L'intitulé de la question est requis.")
        @Size(max = 300, message = "L'intitulé ne peut pas dépasser 300 caractères.")
        String label,

        @NotNull(message = "Le type de question est requis.")
        EventQuestionType type,

        Boolean required,

        @Valid
        List<EventQuestionOptionRequest> options
) {
}
