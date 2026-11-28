package com.carbonx.ledger.api;

import com.carbonx.shared.domain.DomainEvent;

import java.math.BigDecimal;

/** Domain Event carrying full provenance so EV Owners can trace every payout back to batch & report ID. */
public record ProfitDistributedEvent(
        Long distributionId,
        Long sourceReportId,
        Long creditBatchId,
        String batchCode,
        String period,
        int recipientCount,
        BigDecimal totalPayoutUsd
) implements DomainEvent {
    @Override
    public String aggregateType() {
        return "ProfitDistribution";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(distributionId);
    }

    @Override
    public String eventType() {
        return "ledger.profit.distributed";
    }
}
