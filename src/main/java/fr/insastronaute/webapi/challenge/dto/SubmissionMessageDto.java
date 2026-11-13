package fr.insastronaute.webapi.challenge.dto;

import java.time.Instant;
import java.util.UUID;

public record SubmissionMessageDto(
        UUID id,
        String authorType,
        UUID authorUserId,
        String authorName,
        boolean currentUser,
        String content,
        Instant createdAt
) {
}
