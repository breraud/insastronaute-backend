package fr.insastronaute.webapi.leaderboard.service;

import fr.insastronaute.webapi.leaderboard.domain.DepartmentScoringFormula;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DepartmentScoreCalculatorTest {

    private final DepartmentScoreCalculator calculator = new DepartmentScoreCalculator();

    @Test
    void balancedSqrtAppliesAModerateFillRateCompensation() {
        DepartmentScoreResult result = calculator.calculate(600, 30, 100, 100, DepartmentScoringFormula.BALANCED_SQRT);

        assertThat(result.fillRate()).isEqualByComparingTo("0.3");
        assertThat(result.finalScore()).isEqualTo(1095L);
        assertThat(result.coefficient()).isCloseTo(1.825741858, org.assertj.core.data.Offset.offset(0.000000001));
    }

    @Test
    void fullDepartmentsWithTheSameAverageGetTheSameNormalizedScore() {
        assertThat(calculator.calculate(300, 30, 30, 60, DepartmentScoringFormula.BALANCED_SQRT).finalScore()).isEqualTo(600L);
        assertThat(calculator.calculate(600, 60, 60, 60, DepartmentScoringFormula.BALANCED_SQRT).finalScore()).isEqualTo(600L);
    }

    @Test
    void normalizesBalancedCoefficientsAgainstTheLeastCompensatedDepartment() {
        assertThat(calculator.normalizeBalancedCoefficient(2.5, 1.2)).isCloseTo(2.083333333, org.assertj.core.data.Offset.offset(0.000000001));
    }
}
