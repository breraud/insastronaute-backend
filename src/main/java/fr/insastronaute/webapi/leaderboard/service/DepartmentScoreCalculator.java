package fr.insastronaute.webapi.leaderboard.service;

import fr.insastronaute.webapi.leaderboard.domain.DepartmentScoringFormula;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DepartmentScoreCalculator {

    /**
     * Ramène le coefficient équilibré au département le moins compensé.
     *
     * Le classement publié et sa simulation doivent annoncer le même multiplicateur :
     * l'unité représente donc toujours le coefficient participant le plus faible.
     */
    public double normalizeBalancedCoefficient(double coefficient, double baselineCoefficient) {
        return baselineCoefficient > 0d ? coefficient / baselineCoefficient : coefficient;
    }

    public DepartmentScoreResult calculate(
            long rawTotal,
            int registeredCount,
            int theoreticalHeadcount,
            int referenceHeadcount,
            DepartmentScoringFormula formula
    ) {
        if (registeredCount < 0 || theoreticalHeadcount < 1 || referenceHeadcount < 1) {
            throw new IllegalArgumentException("Population counts must be valid.");
        }

        BigDecimal fillRate = BigDecimal.valueOf(registeredCount)
                .divide(BigDecimal.valueOf(theoreticalHeadcount), 12, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);
        BigDecimal average = registeredCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(rawTotal).divide(BigDecimal.valueOf(registeredCount), 12, RoundingMode.HALF_UP);

        double multiplier = registeredCount == 0 && formula != DepartmentScoringFormula.RAW_TOTAL
                ? 0d
                : switch (formula) {
                    case RAW_TOTAL -> 1d;
                    case PER_REGISTERED_NORMALIZED -> referenceHeadcount / (double) registeredCount;
                    case BALANCED_SQRT -> referenceHeadcount / (double) registeredCount * Math.sqrt(fillRate.doubleValue());
                    case FULL_PARTICIPATION -> referenceHeadcount / (double) theoreticalHeadcount;
                };

        long finalScore = BigDecimal.valueOf(rawTotal * multiplier).setScale(0, RoundingMode.HALF_UP).longValue();
        return new DepartmentScoreResult(rawTotal, registeredCount, theoreticalHeadcount, fillRate, average, multiplier, finalScore);
    }
}
