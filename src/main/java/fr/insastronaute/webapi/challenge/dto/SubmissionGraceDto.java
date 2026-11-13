package fr.insastronaute.webapi.challenge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * La grâce accordée sur une soumission refusée.
 *
 * <p>Le délai est facultatif : vingt-quatre heures répondent au cas courant —
 * une preuve refusée après la fermeture du défi, qu'il faut juste refaire.</p>
 */
public record SubmissionGraceDto(
        @Min(value = 1, message = "Le délai accordé doit valoir au moins une heure.")
        @Max(value = 168, message = "Le délai accordé ne peut pas dépasser une semaine.")
        Integer hours,

        /** Ce que le joueur doit corriger. Il le lit dans sa notification. */
        @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères.")
        String reason
) {}
