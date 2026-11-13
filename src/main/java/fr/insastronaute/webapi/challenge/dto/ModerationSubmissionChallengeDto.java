package fr.insastronaute.webapi.challenge.dto;

import java.util.UUID;

public record ModerationSubmissionChallengeDto(
        UUID id,
        String title,
        String description,
        int pointsReward,
        String category,
        String validationMode,
        boolean sensitive,
        int minGroupSize,
        int maxGroupSize
) {
}
