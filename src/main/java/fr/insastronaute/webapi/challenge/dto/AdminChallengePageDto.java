package fr.insastronaute.webapi.challenge.dto;

import java.util.List;

public record AdminChallengePageDto(
        List<AdminChallengeDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long activeCount,
        long draftCount,
        long scheduledCount
) {
}
