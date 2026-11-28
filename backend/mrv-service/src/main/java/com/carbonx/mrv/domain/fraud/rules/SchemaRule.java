package com.carbonx.mrv.domain.fraud.rules;

import com.carbonx.mrv.domain.fraud.FraudFinding;
import com.carbonx.mrv.domain.fraud.FraudRule;
import com.carbonx.mrv.domain.fraud.FraudRuleCode;
import com.carbonx.mrv.domain.report.EmissionDataset;

import java.util.List;

/** DQ1: the upload must carry data rows and all required columns. */
public class SchemaRule implements FraudRule {

    @Override
    public FraudFinding apply(EmissionDataset dataset) {
        List<String> missing = EmissionDataset.REQUIRED_HEADERS.stream()
                .filter(header -> !dataset.headers().contains(header))
                .toList();
        boolean passed = missing.isEmpty() && !dataset.isEmpty();
        int score = passed ? FraudRuleCode.DQ1_SCHEMA.maxScore() : 0;
        String evidence = "rows=" + dataset.rows().size() + ", missingColumns=" + missing;
        return FraudFinding.of(FraudRuleCode.DQ1_SCHEMA, score, passed, evidence);
    }
}
