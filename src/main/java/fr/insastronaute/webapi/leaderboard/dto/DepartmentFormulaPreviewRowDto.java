package fr.insastronaute.webapi.leaderboard.dto;

import fr.insastronaute.webapi.leaderboard.domain.DepartmentScoringFormula;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record DepartmentFormulaPreviewRowDto(
        UUID departmentId,
        String name,
        String shortName,
        Integer theoreticalHeadcount,
        int registeredCount,
        BigDecimal fillRate,
        long rawTotal,
        BigDecimal average,
        Map<DepartmentScoringFormula, FormulaResultDto> formulas
) {
}
