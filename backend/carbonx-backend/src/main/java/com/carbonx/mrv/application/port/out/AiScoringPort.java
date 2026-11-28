package com.carbonx.mrv.application.port.out;

import java.math.BigDecimal;
import java.util.List;

/** Outbound Port: External AI Risk Scoring adapter (e.g., Gemini). */
public interface AiScoringPort {
    AiScoreResult scoreEmissionReport(Long reportId, String period, BigDecimal totalEnergyKwh, List<FraudRuleEnginePort.RuleEvaluation> ruleResults);

    record AiScoreResult(BigDecimal riskScore, String summary) {}
}
