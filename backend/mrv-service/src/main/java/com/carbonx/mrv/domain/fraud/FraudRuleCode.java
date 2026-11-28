package com.carbonx.mrv.domain.fraud;

/**
 * The eight data-quality rules ported from the legacy CarbonX analysis engine (DQ1..DQ8).
 * {@code maxScore} is the weight of the rule in the 75-point scale; the weighted percentage is the
 * deterministic fallback score whenever the LLM is unavailable.
 */
public enum FraudRuleCode {

    DQ1_SCHEMA("DQ1", "Schema & Header Integrity", 15),
    DQ2_PERIOD("DQ2", "Single Valid Reporting Period", 10),
    DQ3_ENERGY("DQ3", "Energy Value Validity", 15),
    DQ4_DUP_PLATE("DQ4", "Duplicate License Plate Detection", 10),
    DQ5_DUP_ROW("DQ5", "Exact Duplicate Row Detection", 5),
    DQ6_OUTLIER_IQR("DQ6", "Energy Outlier Detection (IQR)", 10),
    DQ7_UNIFORMITY_CV("DQ7", "Energy Uniformity (Coefficient of Variation)", 5),
    DQ8_REPEATED_ROUNDED("DQ8", "Repeated Rounded Energy Pattern", 5);

    private final String shortCode;
    private final String displayName;
    private final int maxScore;

    FraudRuleCode(String shortCode, String displayName, int maxScore) {
        this.shortCode = shortCode;
        this.displayName = displayName;
        this.maxScore = maxScore;
    }

    public String shortCode() {
        return shortCode;
    }

    public String displayName() {
        return displayName;
    }

    public int maxScore() {
        return maxScore;
    }
}
