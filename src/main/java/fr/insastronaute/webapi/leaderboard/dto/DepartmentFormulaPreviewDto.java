package fr.insastronaute.webapi.leaderboard.dto;

import fr.insastronaute.webapi.leaderboard.domain.DepartmentScoringFormula;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;

import java.util.List;
import java.util.UUID;

public record DepartmentFormulaPreviewDto(
        LeaderboardMetric metric,
        int referenceHeadcount,
        DepartmentScoringFormula recommendedFormula,
        DepartmentScoringFormula currentFormula,
        List<UUID> missingDepartmentIds,
        List<DepartmentFormulaPreviewRowDto> rows
) {
    public static DepartmentFormulaPreviewDto empty(LeaderboardMetric metric) {
        return new DepartmentFormulaPreviewDto(
                metric, 0, DepartmentScoringFormula.BALANCED_SQRT,
                DepartmentScoringFormula.RAW_TOTAL, List.of(), List.of());
    }
}
