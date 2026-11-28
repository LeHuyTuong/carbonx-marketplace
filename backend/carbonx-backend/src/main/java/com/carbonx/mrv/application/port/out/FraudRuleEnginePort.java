package com.carbonx.mrv.application.port.out;

import java.math.BigDecimal;
import java.util.List;

/** Outbound Port: Pluggable 8-rule Fraud Analysis Engine (DQ1..DQ8). */
public interface FraudRuleEnginePort {
    List<RuleEvaluation> evaluate(Long reportId, List<CsvRow> rows);

    record CsvRow(int rowNumber, String plateNumber, String period, BigDecimal energyKwh) {}

    record RuleEvaluation(String ruleCode, String ruleName, int score, int maxScore, boolean passed, String evidence) {}
}
