package com.carbonx.ledger.domain.wallet;

import java.math.BigDecimal;
import java.time.Instant;

public record ProfitDistribution(
        Long id,
        Long companyId,
        Long sourceReportId,
        String idempotencyKey,
        BigDecimal totalAmount,
        int recipientCount,
        String status,
        Instant createdAt
) {
}
