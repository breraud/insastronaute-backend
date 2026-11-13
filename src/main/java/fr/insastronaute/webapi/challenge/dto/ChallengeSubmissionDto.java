package fr.insastronaute.webapi.challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ChallengeSubmissionDto(
        @NotBlank
        @Pattern(regexp = "^(TEXT|MEDIA)$", message = "Type non valide")
        String proofType,

        List<UUID> proofUploadIds,

        List<UUID> participantUserIds,

        @Size(max = 1000, message = "Le commentaire ne doit pas dépasser 1000 caractères.")
        String comment,

        /**
         * Le joueur declare sa preuve sensible.
         *
         * <p>Le defi sensible est decide par l'organisation, pour tout le monde. Celui
         * qui envoie la photo est parfois seul a savoir qu'elle le devient dans son cas :
         * meme mecanique, a l'echelle d'une preuve. Absent vaut non.</p>
         */
        Boolean sensitive
) {
    public ChallengeSubmissionDto(String proofType, String comment) {
        this(proofType, null, null, comment, null);
    }
}
