package com.carbonx.mrv.domain.fraud.rules;

import com.carbonx.mrv.domain.fraud.FraudFinding;
import com.carbonx.mrv.domain.fraud.FraudRule;
import com.carbonx.mrv.domain.fraud.FraudRuleCode;
import com.carbonx.mrv.domain.report.EmissionDataset;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** DQ6: Tukey IQR fence detects fabricated outliers in the energy column. */
public class EnergyOutlierRule implements FraudRule {

    private static final double IQR_MULTIPLIER = 1.5;

    @Override
    public FraudFinding apply(EmissionDataset dataset) {
        List<Double> values = new ArrayList<>();
        for (BigDecimal energy : dataset.energies()) {
            values.add(energy.doubleValue());
        }
        if (values.size() < 4) {
            String evidence = "rows=" + values.size() + " (insufficient for IQR)";
            return FraudFinding.of(FraudRuleCode.DQ6_OUTLIER_IQR, FraudRuleCode.DQ6_OUTLIER_IQR.maxScore(),
                    true, evidence);
        }
        values.sort(Double::compareTo);
        double q1 = quantile(values, 0.25);
        double q3 = quantile(values, 0.75);
        double iqr = q3 - q1;
        double lower = q1 - IQR_MULTIPLIER * iqr;
        double upper = q3 + IQR_MULTIPLIER * iqr;
        long outliers = values.stream().filter(value -> value < lower || value > upper).count();

        int score;
        if (outliers == 0) {
            score = FraudRuleCode.DQ6_OUTLIER_IQR.maxScore();
        } else if (outliers <= 2) {
            score = 8;
        } else if (outliers <= 5) {
            score = 5;
        } else {
            score = 2;
        }
        String evidence = String.format("q1=%.3f, q3=%.3f, lowerFence=%.3f, upperFence=%.3f, outliers=%d",
                q1, q3, lower, upper, outliers);
        return FraudFinding.of(FraudRuleCode.DQ6_OUTLIER_IQR, score, outliers == 0, evidence);
    }

    private static double quantile(List<Double> sorted, double p) {
        double position = p * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return sorted.get(lower);
        }
        double weight = position - lower;
        return sorted.get(lower) * (1 - weight) + sorted.get(upper) * weight;
    }
}
