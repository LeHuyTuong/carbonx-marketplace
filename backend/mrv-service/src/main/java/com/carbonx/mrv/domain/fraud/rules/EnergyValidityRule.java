package com.carbonx.mrv.domain.fraud.rules;

import com.carbonx.mrv.domain.fraud.FraudFinding;
import com.carbonx.mrv.domain.fraud.FraudRule;
import com.carbonx.mrv.domain.fraud.FraudRuleCode;
import com.carbonx.mrv.domain.report.EmissionDataset;
import com.carbonx.mrv.domain.report.EmissionRow;

import java.math.BigDecimal;

/** DQ3: every energy value must be numeric and strictly positive. */
public class EnergyValidityRule implements FraudRule {

    @Override
    public FraudFinding apply(EmissionDataset dataset) {
        int total = dataset.rows().size();
        int nonNumeric = 0;
        int nonPositive = 0;
        for (EmissionRow row : dataset.rows()) {
            var value = row.energy();
            if (value.isEmpty()) {
                nonNumeric++;
            } else if (value.get().signum() <= 0) {
                nonPositive++;
            }
        }
        int valid = total - nonNumeric - nonPositive;
        boolean perfect = total > 0 && nonNumeric == 0 && nonPositive == 0;

        int score;
        if (perfect) {
            score = FraudRuleCode.DQ3_ENERGY.maxScore();
        } else if (total > 0 && valid >= total * 0.95) {
            score = 10;
        } else if (total > 0 && valid >= total * 0.80) {
            score = 5;
        } else {
            score = 0;
        }
        String evidence = "rows=" + total + ", nonNumeric=" + nonNumeric + ", nonPositive=" + nonPositive
                + ", totalKwh=" + dataset.totalEnergyKwh().stripTrailingZeros().toPlainString();
        return FraudFinding.of(FraudRuleCode.DQ3_ENERGY, score, perfect, evidence);
    }
}
