package fr.insastronaute.webapi.challenge.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlayerSubmissionDetailDto(
        UUID id,
        UUID challengeId,
        String challengeTitle,
        String status,
        Instant submittedAt,
        Instant validatedAt,
        String moderationComment,
        boolean currentUserSubmitter,
        List<SubmissionParticipantDto> participants,
        /**
         * Le groupe est tombe sous la taille minimale du defi.
         *
         * <p>Un refus peut le faire passer sous le seuil : la soumission reste alors
         * en attente de resolution, au chef d'ajouter quelqu'un.</p>
         */
        boolean belowMinimumGroupSize,
        int minGroupSize,
        int maxGroupSize
) {
}
