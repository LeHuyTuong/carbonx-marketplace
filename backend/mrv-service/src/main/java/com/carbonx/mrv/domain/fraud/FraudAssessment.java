package com.carbonx.mrv.domain.fraud;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** The complete DQ1..DQ8 assessment of one uploaded dataset. */
public record FraudAssessment(List<FraudFinding> findings) {

    public FraudAssessment {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public int totalScore() {
        return findings.stream().mapToInt(FraudFinding::score).sum();
    }

    public int totalMaxScore() {
        return findings.stream().mapToInt(FraudFinding::maxScore).sum();
    }

    /** Weighted 0..100 score used as the deterministic DQ fallback when the LLM is down. */
    public BigDecimal weightedScore() {
        int max = totalMaxScore();
        if (max <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(totalScore())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(max), 2, RoundingMode.HALF_UP);
    }

    public boolean isClean() {
        return findings.stream().allMatch(FraudFinding::passed);
    }

    public String summary() {
        return findings.stream()
                .map(finding -> finding.ruleCode() + "=" + finding.score() + "/" + finding.maxScore())
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
