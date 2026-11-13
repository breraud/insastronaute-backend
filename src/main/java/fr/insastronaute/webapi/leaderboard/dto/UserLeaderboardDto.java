package fr.insastronaute.webapi.leaderboard.dto;

import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;

import java.time.Instant;
import java.util.List;

public record UserLeaderboardDto(
        LeaderboardMetric metric,
        Instant calculatedAt,
        List<UserLeaderboardEntryDto> entries
) {
}
