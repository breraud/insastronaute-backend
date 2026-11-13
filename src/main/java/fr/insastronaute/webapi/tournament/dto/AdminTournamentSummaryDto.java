package fr.insastronaute.webapi.tournament.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminTournamentSummaryDto(
        UUID eventId,
        String title,
        String location,
        String coverImageUrl,
        Instant startsAt,
        Instant endsAt,
        Instant publishedAt,
        boolean registrationEnabled,
        boolean hasEnded,
        long registeredCount,
        long waitlistedCount,
        long attendedCount,
        long attendanceUnmarkedCount,
        boolean settled,
        Instant settledAt,
        int participationPoints,
        long totalPointsAwarded
) {
}
