package com.carbonx.mrv.domain.fraud.rules;

import com.carbonx.mrv.domain.fraud.FraudFinding;
import com.carbonx.mrv.domain.fraud.FraudRule;
import com.carbonx.mrv.domain.fraud.FraudRuleCode;
import com.carbonx.mrv.domain.report.EmissionDataset;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/** DQ8: many repeated rounded energy values reveal a duplicated or padded dataset. */
public class RepeatedRoundedEnergyRule implements FraudRule {

    private static final int ROUNDING_SCALE = 1;

    @Override
    public FraudFinding apply(EmissionDataset dataset) {
        Map<String, Integer> frequency = new HashMap<>();
        for (BigDecimal energy : dataset.energies()) {
            String key = energy.setScale(ROUNDING_SCALE, RoundingMode.HALF_UP).toPlainString();
            frequency.merge(key, 1, Integer::sum);
        }
        long repeatedKeys = frequency.values().stream().filter(count -> count > 1).count();

        int score;
        if (repeatedKeys == 0) {
            score = FraudRuleCode.DQ8_REPEATED_ROUNDED.maxScore();
        } else if (repeatedKeys <= 3) {
            score = 3;
        } else {
            score = 1;
        }
        String evidence = "repeatedRoundedKeys=" + repeatedKeys + ", distinctValues=" + frequency.size()
                + ", scale=" + ROUNDING_SCALE;
        return FraudFinding.of(FraudRuleCode.DQ8_REPEATED_ROUNDED, score, repeatedKeys == 0, evidence);
    }
}
