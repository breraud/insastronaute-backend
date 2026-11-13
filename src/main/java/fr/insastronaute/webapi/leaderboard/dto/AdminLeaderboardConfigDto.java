package fr.insastronaute.webapi.leaderboard.dto;

import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardScope;
import fr.insastronaute.webapi.leaderboard.domain.DepartmentScoringFormula;

import java.time.Instant;

public record AdminLeaderboardConfigDto(
        LeaderboardScope scope,
        LeaderboardMetric metric,
        Integer refreshDelayMinutes,
        Boolean autoRefreshEnabled,
        Instant lastReleasedAt,
        DepartmentScoringFormula formula,
        Integer totalWeightPercent,
        /** Ce par quoi ses points sont multiplies avant le poids, dans le total seulement. */
        Integer totalScaleFactor
) {
    public AdminLeaderboardConfigDto(
            LeaderboardScope scope,
            LeaderboardMetric metric,
            Integer refreshDelayMinutes,
            Boolean autoRefreshEnabled,
            Instant lastReleasedAt,
            DepartmentScoringFormula formula,
            Integer totalWeightPercent
    ) {
        this(scope, metric, refreshDelayMinutes, autoRefreshEnabled, lastReleasedAt, formula, totalWeightPercent, 1);
    }

    public AdminLeaderboardConfigDto(
            LeaderboardScope scope,
            LeaderboardMetric metric,
            Integer refreshDelayMinutes,
            Boolean autoRefreshEnabled,
            Instant lastReleasedAt,
            DepartmentScoringFormula formula
    ) {
        this(scope, metric, refreshDelayMinutes, autoRefreshEnabled, lastReleasedAt, formula, 20);
    }

    public AdminLeaderboardConfigDto(
            LeaderboardScope scope,
            LeaderboardMetric metric,
            Integer refreshDelayMinutes,
            Boolean autoRefreshEnabled,
            Instant lastReleasedAt
    ) {
        this(scope, metric, refreshDelayMinutes, autoRefreshEnabled, lastReleasedAt, DepartmentScoringFormula.RAW_TOTAL, 20);
    }
}
