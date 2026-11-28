package com.carbonx.mrv.domain.fraud.rules;

import com.carbonx.mrv.domain.fraud.FraudFinding;
import com.carbonx.mrv.domain.fraud.FraudRule;
import com.carbonx.mrv.domain.fraud.FraudRuleCode;
import com.carbonx.mrv.domain.report.EmissionDataset;

import java.math.BigDecimal;
import java.util.List;

/** DQ7: a coefficient of variation below 5% indicates machine-generated uniformity. */
public class EnergyUniformityRule implements FraudRule {

    private static final double CV_THRESHOLD = 0.05;

    @Override
    public FraudFinding apply(EmissionDataset dataset) {
        List<BigDecimal> energies = dataset.energies();
        if (energies.size() < 2) {
            String evidence = "samples=" + energies.size() + " (insufficient for CV)";
            return FraudFinding.of(FraudRuleCode.DQ7_UNIFORMITY_CV, FraudRuleCode.DQ7_UNIFORMITY_CV.maxScore(),
                    true, evidence);
        }
        double mean = energies.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double variance = energies.stream()
                .mapToDouble(value -> Math.pow(value.doubleValue() - mean, 2))
                .sum() / (energies.size() - 1);
        double standardDeviation = Math.sqrt(variance);
        double coefficient = mean == 0 ? 0 : standardDeviation / mean;
        boolean suspicious = coefficient < CV_THRESHOLD;

        String evidence = String.format("mean=%.6f, std=%.6f, cv=%.6f, threshold=%.6f",
                mean, standardDeviation, coefficient, CV_THRESHOLD);
        return FraudFinding.of(FraudRuleCode.DQ7_UNIFORMITY_CV, suspicious ? 2
                : FraudRuleCode.DQ7_UNIFORMITY_CV.maxScore(), !suspicious, evidence);
    }
}
