package fr.insastronaute.webapi.leaderboard.dto;

import fr.insastronaute.webapi.leaderboard.domain.DepartmentScoringFormula;
import jakarta.validation.constraints.Positive;

public record LeaderboardConfigUpdateDto(
        @Positive(message = "Le délai de rafraîchissement doit être supérieur ou égal à 1 minute.")
        Integer refreshDelayMinutes,
        Boolean autoRefreshEnabled,
        DepartmentScoringFormula formula
) {
    public LeaderboardConfigUpdateDto(Integer refreshDelayMinutes, Boolean autoRefreshEnabled) {
        this(refreshDelayMinutes, autoRefreshEnabled, null);
    }
}
