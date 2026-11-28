package com.carbonx.common.events;

import com.carbonx.common.domain.DomainEvent;
import java.math.BigDecimal;

/** Canonical Integration Event Contracts shared over Apache Kafka across Microservices. */
public final class IntegrationEvents {
    private IntegrationEvents() {}

    public record VehicleRegisteredEvent(Long vehicleId, Long evOwnerId, Long companyId, String plateNumber, String brand, String model) implements DomainEvent {
        public String aggregateType() { return "Vehicle"; }
        public String aggregateId() { return String.valueOf(vehicleId); }
        public String eventType() { return "identity.vehicle.registered"; }
    }

    public record EmissionReportApprovedEvent(
            Long reportId, Long projectId, String projectTitle, Long companyId, String period,
            BigDecimal totalEnergyKwh, BigDecimal totalCo2Kg, BigDecimal emissionFactor,
            BigDecimal bufferRate, BigDecimal uncertaintyRate, BigDecimal leakageRate
    ) implements DomainEvent {
        public String aggregateType() { return "EmissionReport"; }
        public String aggregateId() { return String.valueOf(reportId); }
        public String eventType() { return "mrv.emission-report.approved"; }
    }

    public record CreditBatchIssuedEvent(
            Long batchId, String batchCode, Long sourceReportId, Long issuerCompanyId,
            long totalIssuedQty, long serialStart, long serialEnd
    ) implements DomainEvent {
        public String aggregateType() { return "CreditBatch"; }
        public String aggregateId() { return String.valueOf(batchId); }
        public String eventType() { return "registry.credit-batch.issued"; }
    }

    public record OrderSettledEvent(
            Long orderId, String orderCode, Long listingId, Long batchId,
            Long buyerCompanyId, Long sellerCompanyId, long quantity,
            BigDecimal grossAmount, BigDecimal platformFee
    ) implements DomainEvent {
        public String aggregateType() { return "TradeOrder"; }
        public String aggregateId() { return String.valueOf(orderId); }
        public String eventType() { return "trading.order.settled"; }
    }

    public record ProfitDistributedEvent(
            Long distributionId, Long sourceReportId, Long creditBatchId, String batchCode,
            String period, int recipientCount, BigDecimal totalPayoutUsd
    ) implements DomainEvent {
        public String aggregateType() { return "ProfitDistribution"; }
        public String aggregateId() { return String.valueOf(distributionId); }
        public String eventType() { return "ledger.profit.distributed"; }
    }
}
