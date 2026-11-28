package com.carbonx.registry.domain.credit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CreditFormulaTest {

    private final CreditFormula formula = new CreditFormula();

    @Test
    @DisplayName("VM0038: gross tCO2e minus buffer/uncertainty/leakage, floored to whole credits")
    void appliesDeductionsAndFloorsToWholeCredits() {
        IssuanceComputation result = formula.compute(new BigDecimal("100000.0000"),
                new BigDecimal("67660.0000"), new BigDecimal("0.6766"),
                new BigDecimal("0.05"), new BigDecimal("0.03"), new BigDecimal("0.02"), BigDecimal.ZERO);

        assertThat(result.grossTco2e()).isEqualByComparingTo("67.660000");
        assertThat(result.netTco2e()).isEqualByComparingTo("60.894");
        assertThat(result.credits()).isEqualTo(60);
        assertThat(result.residualTco2e()).isEqualByComparingTo("0.894");
        assertThat(result.isIssuable()).isTrue();
    }

    @Test
    @DisplayName("The fractional tonnage is never rounded up into a credit")
    void neverRoundsTonnageUp() {
        IssuanceComputation result = formula.compute(null, new BigDecimal("60999"),
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(result.netTco2e()).isEqualByComparingTo("60.999");
        assertThat(result.credits()).isEqualTo(60);
        assertThat(result.residualTco2e()).isEqualByComparingTo("0.999");
    }

    @Test
    @DisplayName("The previous period residual is carried into the next batch")
    void carriesThePreviousPeriodResidual() {
        IssuanceComputation result = formula.compute(null, new BigDecimal("67660"), null,
                new BigDecimal("0.05"), new BigDecimal("0.03"), new BigDecimal("0.02"), new BigDecimal("0.500"));

        assertThat(result.netTco2e()).isEqualByComparingTo("61.394");
        assertThat(result.credits()).isEqualTo(61);
        assertThat(result.residualTco2e()).isEqualByComparingTo("0.394");
    }

    @Test
    @DisplayName("Without an approved tonnage the activity energy is converted with the project factor")
    void convertsEnergyWhenTonnageIsMissing() {
        IssuanceComputation result = formula.compute(new BigDecimal("100000"), null, new BigDecimal("0.6766"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);

        assertThat(result.grossTco2e()).isEqualByComparingTo("67.660000");
        assertThat(result.credits()).isEqualTo(67);
    }

    @Test
    @DisplayName("Deductions that exhaust the tonnage cannot mint a credit")
    void exhaustsTheTonnage() {
        IssuanceComputation result = formula.compute(null, new BigDecimal("67660"), null,
                new BigDecimal("0.60"), new BigDecimal("0.60"), new BigDecimal("0.60"), BigDecimal.ZERO);

        assertThat(result.netTco2e()).isEqualByComparingTo("0.000");
        assertThat(result.credits()).isZero();
        assertThat(result.isIssuable()).isFalse();
    }

    @Test
    @DisplayName("A legacy percentage deduction is normalised to a fraction")
    void acceptsPercentageDeductions() {
        IssuanceComputation fractions = formula.compute(null, new BigDecimal("67660"), null,
                new BigDecimal("0.05"), new BigDecimal("0.03"), new BigDecimal("0.02"), BigDecimal.ZERO);
        IssuanceComputation percentages = formula.compute(null, new BigDecimal("67660"), null,
                new BigDecimal("5"), new BigDecimal("3"), new BigDecimal("2"), BigDecimal.ZERO);

        assertThat(percentages.netTco2e()).isEqualByComparingTo(fractions.netTco2e());
        assertThat(percentages.credits()).isEqualTo(fractions.credits());
    }
}
