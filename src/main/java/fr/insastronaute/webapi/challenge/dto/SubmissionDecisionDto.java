package fr.insastronaute.webapi.challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmissionDecisionDto(
        @NotBlank
        @Pattern(regexp = "^(VALIDATED|REJECTED)$", message = "Le statut doit être VALIDATED ou REJECTED")
        String status,

        @Size(max = 1000, message = "Le commentaire de modération ne doit pas dépasser 1000 caractères.")
        String moderationComment,

        Integer bonusPoints
) {}
