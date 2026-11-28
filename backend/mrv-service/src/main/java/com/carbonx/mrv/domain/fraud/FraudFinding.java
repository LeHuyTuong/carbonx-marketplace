package com.carbonx.mrv.domain.fraud;

import com.carbonx.mrv.domain.shared.DomainValidationException;

/** Outcome of one data-quality rule, persisted in {@code fraud_analyses}. */
public record FraudFinding(
        String ruleCode,
        String ruleName,
        int score,
        int maxScore,
        boolean passed,
        String evidence
) {

    public FraudFinding {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new DomainValidationException("ruleCode is required");
        }
        if (ruleName == null || ruleName.isBlank()) {
            throw new DomainValidationException("ruleName is required");
        }
        if (maxScore <= 0) {
            throw new DomainValidationException("maxScore must be positive");
        }
        if (score < 0 || score > maxScore) {
            throw new DomainValidationException("score must be between 0 and maxScore");
        }
    }

    public static FraudFinding of(FraudRuleCode code, int score, boolean passed, String evidence) {
        int bounded = Math.max(0, Math.min(code.maxScore(), score));
        return new FraudFinding(code.name(), code.displayName(), bounded, code.maxScore(), passed, evidence);
    }

    public boolean isCritical() {
        return score == 0;
    }
}
