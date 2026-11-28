package com.carbonx.mrv.domain.fraud.rules;

import com.carbonx.mrv.domain.fraud.FraudFinding;
import com.carbonx.mrv.domain.fraud.FraudRule;
import com.carbonx.mrv.domain.fraud.FraudRuleCode;
import com.carbonx.mrv.domain.report.EmissionDataset;

import java.util.HashSet;
import java.util.Set;

/** DQ4: the same vehicle plate may not appear twice in one reporting file. */
public class DuplicatePlateRule implements FraudRule {

    @Override
    public FraudFinding apply(EmissionDataset dataset) {
        int duplicates = countDuplicateRows(dataset);

        int score;
        if (duplicates == 0) {
            score = FraudRuleCode.DQ4_DUP_PLATE.maxScore();
        } else if (duplicates <= 2) {
            score = 7;
        } else if (duplicates <= 5) {
            score = 3;
        } else {
            score = 0;
        }
        String evidence = "duplicatePlateRows=" + duplicates + ", distinctPlates=" + dataset.vehicleCount();
        return FraudFinding.of(FraudRuleCode.DQ4_DUP_PLATE, score, duplicates == 0, evidence);
    }

    private static int countDuplicateRows(EmissionDataset dataset) {
        Set<String> seen = new HashSet<>();
        int duplicates = 0;
        for (var row : dataset.rows()) {
            String plate = row.normalizedPlate();
            if (plate.isEmpty()) {
                continue;
            }
            if (!seen.add(plate)) {
                duplicates++;
            }
        }
        return duplicates;
    }
}
