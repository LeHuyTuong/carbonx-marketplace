package com.carbonx.mrv.api;

import com.carbonx.shared.domain.DomainEvent;

import java.math.BigDecimal;

/** Domain Event published to Outbox when Admin approves an EmissionReport for credit issuance. */
public record EmissionReportApprovedEvent(
        Long reportId,
        Long projectId,
        String projectTitle,
        Long companyId,
        String period,
        BigDecimal totalEnergyKwh,
        BigDecimal totalCo2Kg,
        BigDecimal emissionFactor,
        BigDecimal bufferRate,
        BigDecimal uncertaintyRate,
        BigDecimal leakageRate
) implements DomainEvent {
    @Override
    public String aggregateType() {
        return "EmissionReport";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(reportId);
    }

    @Override
    public String eventType() {
        return "mrv.emission-report.approved";
    }
}
