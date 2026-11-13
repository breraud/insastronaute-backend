package fr.insastronaute.webapi.challenge.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ModerationSubmissionDetailDto(
        UUID submissionId,
        String status,
        Instant submittedAt,
        Instant validatedAt,
        String submitterName,
        String submitterDepartment,
        String proofType,
        String proofData,
        List<ProofMediaDto> proofMedia,
        /**
         * Quand les fichiers ont ete effaces, apres arbitrage sur un defi sensible.
         *
         * <p>Null tant qu'ils sont la. Sans cette date, une preuve purgee ressemblerait a
         * une preuve qui n'a jamais eu de fichier.</p>
         */
        Instant proofMediaPurgedAt,
        String comment,
        String moderationComment,
        int bonusPoints,
        boolean messagesEnabled,
        boolean reviewLocked,
        boolean reviewLockedByCurrentUser,
        Instant reviewStartedAt,
        ModerationSubmissionChallengeDto challenge,
        List<SubmissionParticipantDto> participants,
        List<SubmissionMessageDto> messages
) {
}
