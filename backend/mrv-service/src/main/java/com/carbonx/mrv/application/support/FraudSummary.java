package com.carbonx.mrv.application.support;

import com.carbonx.mrv.domain.fraud.FraudFinding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JSON contract shared with the AI adapter: the exact DQ1..DQ8 findings are handed to the LLM and,
 * when the LLM is unavailable, the fallback recomputes the weighted score from the same payload.
 */
public record FraudSummary(List<Rule> rules) {

    public record Rule(String code, String name, int score, int maxScore, boolean passed, String evidence) {
        public Rule {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("rule code is required");
            }
            if (maxScore <= 0 || score < 0 || score > maxScore) {
                throw new IllegalArgumentException("invalid rule score " + score + "/" + maxScore);
            }
        }
    }

    public FraudSummary {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static FraudSummary of(List<FraudFinding> findings) {
        return new FraudSummary(findings.stream()
                .map(finding -> new Rule(finding.ruleCode(), finding.ruleName(), finding.score(),
                        finding.maxScore(), finding.passed(), finding.evidence()))
                .toList());
    }

    public int totalScore() {
        return rules.stream().mapToInt(Rule::score).sum();
    }

    public int totalMaxScore() {
        return rules.stream().mapToInt(Rule::maxScore).sum();
    }

    /** Weighted 0..100 score; identical to {@code FraudAssessment.weightedScore()}. */
    public BigDecimal weightedScore() {
        int max = totalMaxScore();
        if (max <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(totalScore())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(max), 2, RoundingMode.HALF_UP);
    }

    public String describe() {
        return rules.stream()
                .map(rule -> rule.code() + "=" + rule.score() + "/" + rule.maxScore())
                .collect(Collectors.joining(", "));
    }
}
