package com.carbonx.mrv.domain.fraud.rules;

import com.carbonx.mrv.domain.fraud.FraudFinding;
import com.carbonx.mrv.domain.fraud.FraudRule;
import com.carbonx.mrv.domain.fraud.FraudRuleCode;
import com.carbonx.mrv.domain.report.EmissionDataset;

import java.util.HashSet;
import java.util.Set;

/** DQ5: byte-identical rows are a classic copy-paste fabrication pattern. */
public class ExactDuplicateRowRule implements FraudRule {

    @Override
    public FraudFinding apply(EmissionDataset dataset) {
        Set<String> signatures = new HashSet<>();
        int duplicates = 0;
        for (var row : dataset.rows()) {
            if (!signatures.add(row.signature())) {
                duplicates++;
            }
        }
        int score;
        if (duplicates == 0) {
            score = FraudRuleCode.DQ5_DUP_ROW.maxScore();
        } else if (duplicates <= 2) {
            score = 3;
        } else {
            score = 0;
        }
        String evidence = "duplicateRows=" + duplicates + ", rows=" + dataset.rows().size();
        return FraudFinding.of(FraudRuleCode.DQ5_DUP_ROW, score, duplicates == 0, evidence);
    }
}
