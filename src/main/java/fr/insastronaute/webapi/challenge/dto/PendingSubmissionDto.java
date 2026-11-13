package fr.insastronaute.webapi.challenge.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PendingSubmissionDto(
        UUID submissionId,
        String challengeTitle,
        String playerName,
        String playerDepartment,
        String proofType,
        String proofData,
        List<ProofMediaDto> proofMedia,
        List<SubmissionParticipantDto> participants,
        String comment,
        String moderationComment,
        boolean reviewLocked,
        boolean reviewLockedByCurrentUser,
        Instant reviewStartedAt,
        Instant submittedAt
) {}
