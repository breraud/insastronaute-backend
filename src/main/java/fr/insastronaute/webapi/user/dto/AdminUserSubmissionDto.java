package fr.insastronaute.webapi.user.dto;

import fr.insastronaute.webapi.challenge.dto.ModerationSubmissionChallengeDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionParticipantDto;
import fr.insastronaute.webapi.challenge.dto.ProofMediaDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserSubmissionDto(
        UUID submissionId,
        String status,
        Instant submittedAt,
        Instant validatedAt,
        UUID submitterUserId,
        String submitterName,
        String submitterEmail,
        boolean participantOnly,
        String proofType,
        String proofData,
        List<ProofMediaDto> proofMedia,
        String comment,
        String moderationComment,
        int bonusPoints,
        boolean messagesEnabled,
        ModerationSubmissionChallengeDto challenge,
        List<SubmissionParticipantDto> participants
) {
}
