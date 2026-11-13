package fr.insastronaute.webapi.challenge.dto;

import java.util.UUID;

public record ModerationParticipantCandidateDto(
        UUID userId,
        String displayName,
        String email,
        boolean eligible,
        String reason
) {
}
