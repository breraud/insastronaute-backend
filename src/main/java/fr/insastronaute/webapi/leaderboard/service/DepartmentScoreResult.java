package fr.insastronaute.webapi.leaderboard.service;

import java.math.BigDecimal;

public record DepartmentScoreResult(
        long rawTotal,
        int registeredCount,
        int theoreticalHeadcount,
        BigDecimal fillRate,
        BigDecimal average,
        double coefficient,
        long finalScore
) {
}
