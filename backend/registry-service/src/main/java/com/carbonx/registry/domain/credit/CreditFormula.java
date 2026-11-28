package com.carbonx.registry.domain.credit;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * VM0038 issuance arithmetic. Gross emissions are converted to tCO2e, reduced by the buffer
 * reserve, uncertainty and leakage deductions, carried over with the previous period's residual and
 * floored to whole credits. The remainder is never lost: it is stored as {@code residual_tco2e} on
 * the batch and added to the next batch of the same project/company/vintage scope.
 *
 * <p>Deliberately pure: the formula takes only the approved report figures, so the same inputs
 * always reproduce the same batch (audit requirement).</p>
 */
public final class CreditFormula {

    private static final BigDecimal KG_PER_TONNE = new BigDecimal("1000");
    private static final RoundingMode DOWN = RoundingMode.DOWN;
    private static final int TONNAGE_SCALE = 6;
    private static final int NET_SCALE = 3;
    private static final int PERCENT_BASE = 100;

    public IssuanceComputation compute(BigDecimal totalEnergyKwh, BigDecimal totalCo2Kg,
                                       BigDecimal emissionFactor, BigDecimal bufferRate,
                                       BigDecimal uncertaintyRate, BigDecimal leakageRate,
                                       BigDecimal carriedResidual) {
        BigDecimal grossKg = grossKg(totalEnergyKwh, totalCo2Kg, emissionFactor);
        BigDecimal grossT = grossKg.divide(KG_PER_TONNE, TONNAGE_SCALE, DOWN);
        BigDecimal multiplier = multiplier(bufferRate, uncertaintyRate, leakageRate);
        BigDecimal carried = nonNegative(carriedResidual);

        BigDecimal netT = grossT.multiply(multiplier).add(carried).setScale(NET_SCALE, DOWN);
        long credits = netT.setScale(0, DOWN).longValueExact();
        BigDecimal residual = netT.subtract(BigDecimal.valueOf(credits)).setScale(NET_SCALE, DOWN);
        return new IssuanceComputation(grossT, netT, residual, credits);
    }

    /** Approved CO2e wins; otherwise the activity energy is converted with the project factor. */
    private static BigDecimal grossKg(BigDecimal totalEnergyKwh, BigDecimal totalCo2Kg, BigDecimal emissionFactor) {
        BigDecimal co2Kg = nonNegative(totalCo2Kg);
        if (co2Kg.signum() > 0) {
            return co2Kg;
        }
        BigDecimal energy = nonNegative(totalEnergyKwh);
        BigDecimal factor = nonNegative(emissionFactor);
        if (energy.signum() <= 0 || factor.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return energy.multiply(factor);
    }

    /**
     * Deductions are accepted either as a fraction ({@code 0.05}) or as a percentage ({@code 5}):
     * the platform stores fractions, but a legacy extract may carry whole percentages.
     */
    private static BigDecimal multiplier(BigDecimal bufferRate, BigDecimal uncertaintyRate, BigDecimal leakageRate) {
        BigDecimal deductions = rate(bufferRate).add(rate(uncertaintyRate)).add(rate(leakageRate));
        BigDecimal multiplier = BigDecimal.ONE.subtract(deductions);
        return multiplier.signum() < 0 ? BigDecimal.ZERO : multiplier;
    }

    private static BigDecimal rate(BigDecimal value) {
        BigDecimal rate = nonNegative(value);
        if (rate.compareTo(BigDecimal.ONE) > 0) {
            if (rate.compareTo(BigDecimal.valueOf(PERCENT_BASE)) > 0) {
                return BigDecimal.ONE;
            }
            return rate.divide(BigDecimal.valueOf(PERCENT_BASE), 6, RoundingMode.HALF_UP);
        }
        return rate;
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }
}
