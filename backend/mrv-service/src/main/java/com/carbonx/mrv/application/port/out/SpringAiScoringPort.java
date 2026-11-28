package com.carbonx.mrv.application.port.out;

import java.math.BigDecimal;

/** Spring AI ChatClient Port guarded by Resilience4j @CircuitBreaker, @Bulkhead & @RateLimiter. */
public interface SpringAiScoringPort {
    AiSuggestResult evaluateEmissionReport(Long reportId, String period, BigDecimal totalEnergyKwh, String fraudSummaryJson);
    String chatWithAssistant(String roleContext, String question);

    record AiSuggestResult(BigDecimal aiPreScore, String aiVersion, String aiPreNotes) {}
}
