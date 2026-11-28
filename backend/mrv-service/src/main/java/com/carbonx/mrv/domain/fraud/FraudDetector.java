package com.carbonx.mrv.domain.fraud;

import com.carbonx.mrv.domain.report.EmissionDataset;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs the eight deterministic data-quality rules (DQ1..DQ8) in a fixed order. The detector is a
 * pure domain object: no Spring, no I/O, so the same scoring is used by the AI fallback and tests.
 */
public final class FraudDetector {

    private final List<FraudRule> rules;

    public FraudDetector() {
        this(List.of(
                new com.carbonx.mrv.domain.fraud.rules.SchemaRule(),
                new com.carbonx.mrv.domain.fraud.rules.PeriodRule(),
                new com.carbonx.mrv.domain.fraud.rules.EnergyValidityRule(),
                new com.carbonx.mrv.domain.fraud.rules.DuplicatePlateRule(),
                new com.carbonx.mrv.domain.fraud.rules.ExactDuplicateRowRule(),
                new com.carbonx.mrv.domain.fraud.rules.EnergyOutlierRule(),
                new com.carbonx.mrv.domain.fraud.rules.EnergyUniformityRule(),
                new com.carbonx.mrv.domain.fraud.rules.RepeatedRoundedEnergyRule()
        ));
    }

    public FraudDetector(List<FraudRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public FraudAssessment assess(EmissionDataset dataset) {
        List<FraudFinding> findings = rules.stream()
                .map(rule -> rule.apply(dataset))
                .collect(Collectors.toList());
        return new FraudAssessment(findings);
    }
}
