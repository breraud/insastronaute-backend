package fr.insastronaute.webapi.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Demande de refus d'une soumission de défi déjà validée")
public record AdminRejectChallengeSubmissionRequest(
        @Schema(description = "Commentaire expliquant le motif du refus (optionnel, affiché au joueur)")
        @Size(max = 4000, message = "Le commentaire ne peut pas dépasser 4000 caractères.")
        String comment
) {
}
