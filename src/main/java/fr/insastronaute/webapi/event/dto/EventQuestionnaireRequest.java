package fr.insastronaute.webapi.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The whole questionnaire, replaced in one call. Questions and options carrying
 * an id are updated in place, so editing a label never costs the answers already
 * collected; those left out are removed along with their answers.
 */
public record EventQuestionnaireRequest(
        @NotNull(message = "La liste de questions est requise.")
        @Valid
        List<EventQuestionRequest> questions
) {
}
