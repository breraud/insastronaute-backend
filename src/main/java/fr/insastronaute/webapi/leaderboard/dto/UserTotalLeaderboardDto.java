package fr.insastronaute.webapi.leaderboard.dto;

import java.time.Instant;
import java.util.List;

public record UserTotalLeaderboardDto(
        Instant calculatedAt,
        List<UserLeaderboardEntryDto> entries
) {
}
