package com.carbonx.registry.api;

import com.carbonx.shared.domain.DomainEvent;

public record CreditBatchIssuedEvent(
        Long batchId,
        String batchCode,
        Long sourceReportId,
        Long issuerCompanyId,
        long totalIssuedQty,
        long serialStart,
        long serialEnd
) implements DomainEvent {
    @Override
    public String aggregateType() {
        return "CreditBatch";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(batchId);
    }

    @Override
    public String eventType() {
        return "registry.credit-batch.issued";
    }
}
