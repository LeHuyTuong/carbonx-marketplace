package com.carbonx.mrv.domain.fraud.rules;

import com.carbonx.mrv.domain.fraud.FraudFinding;
import com.carbonx.mrv.domain.fraud.FraudRule;
import com.carbonx.mrv.domain.fraud.FraudRuleCode;
import com.carbonx.mrv.domain.report.EmissionDataset;
import com.carbonx.mrv.domain.report.EmissionRow;

import java.util.LinkedHashSet;
import java.util.Set;

/** DQ2: every row must carry the same, valid {@code YYYY-MM} reporting period. */
public class PeriodRule implements FraudRule {

    @Override
    public FraudFinding apply(EmissionDataset dataset) {
        Set<String> validPeriods = new LinkedHashSet<>();
        int invalidRows = 0;
        for (EmissionRow row : dataset.rows()) {
            String period = row.trimmedPeriod();
            if (EmissionDataset.isValidPeriod(period)) {
                validPeriods.add(period);
            } else {
                invalidRows++;
            }
        }
        int total = dataset.rows().size();
        boolean singleValid = invalidRows == 0 && validPeriods.size() == 1;

        int score;
        if (singleValid) {
            score = FraudRuleCode.DQ2_PERIOD.maxScore();
        } else if (invalidRows == 0) {
            score = 5;
        } else if (total > 0 && invalidRows <= Math.ceil(total * 0.05)) {
            score = 3;
        } else {
            score = 0;
        }
        String evidence = "distinctValidPeriods=" + validPeriods + ", invalidRows=" + invalidRows + "/" + total;
        return FraudFinding.of(FraudRuleCode.DQ2_PERIOD, score, singleValid, evidence);
    }
}
