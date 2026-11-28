package com.carbonx.common.events;

import com.carbonx.common.domain.DomainEvent;
import java.math.BigDecimal;

/** Canonical Integration Event Contracts shared over Apache Kafka across Microservices. */
public final class IntegrationEvents {
    private IntegrationEvents() {}

    public record VehicleRegisteredEvent(Long vehicleId, Long evOwnerId, Long companyId, String plateNumber, String brand, String model) implements DomainEvent {
        @Override
        public String aggregateType() { return IntegrationAggregateType.VEHICLE.wireName(); }
        @Override
        public String aggregateId() { return String.valueOf(vehicleId); }
        @Override
        public String eventType() { return IntegrationEventType.IDENTITY_VEHICLE_REGISTERED.wireName(); }
    }

    public record EmissionReportApprovedEvent(
            Long reportId, Long projectId, String projectTitle, Long companyId, String period,
            BigDecimal totalEnergyKwh, BigDecimal totalCo2Kg, BigDecimal emissionFactor,
            BigDecimal bufferRate, BigDecimal uncertaintyRate, BigDecimal leakageRate
    ) implements DomainEvent {
        @Override
        public String aggregateType() { return IntegrationAggregateType.EMISSION_REPORT.wireName(); }
        @Override
        public String aggregateId() { return String.valueOf(reportId); }
        @Override
        public String eventType() { return IntegrationEventType.MRV_EMISSION_REPORT_APPROVED.wireName(); }
    }

    public record CreditBatchIssuedEvent(
            Long batchId, String batchCode, Long sourceReportId, Long issuerCompanyId,
            long totalIssuedQty, long serialStart, long serialEnd
    ) implements DomainEvent {
        @Override
        public String aggregateType() { return IntegrationAggregateType.CREDIT_BATCH.wireName(); }
        @Override
        public String aggregateId() { return String.valueOf(batchId); }
        @Override
        public String eventType() { return IntegrationEventType.REGISTRY_CREDIT_BATCH_ISSUED.wireName(); }
    }

    public record OrderSettledEvent(
            Long orderId, String orderCode, Long listingId, Long batchId,
            Long buyerCompanyId, Long sellerCompanyId, long quantity,
            BigDecimal grossAmount, BigDecimal platformFee
    ) implements DomainEvent {
        @Override
        public String aggregateType() { return IntegrationAggregateType.TRADE_ORDER.wireName(); }
        @Override
        public String aggregateId() { return String.valueOf(orderId); }
        @Override
        public String eventType() { return IntegrationEventType.TRADING_ORDER_SETTLED.wireName(); }
    }

    public record OrderCompensatedEvent(
            String sagaId, Long orderId, String orderCode, Long listingId,
            String failedStep, String reason
    ) implements DomainEvent {
        @Override
        public String aggregateType() { return IntegrationAggregateType.ORDER_SAGA.wireName(); }
        @Override
        public String aggregateId() { return sagaId; }
        @Override
        public String eventType() { return IntegrationEventType.TRADING_ORDER_COMPENSATED.wireName(); }
    }

    public record ProfitDistributedEvent(
            Long distributionId, Long sourceReportId, Long creditBatchId, String batchCode,
            String period, int recipientCount, BigDecimal totalPayoutUsd
    ) implements DomainEvent {
        @Override
        public String aggregateType() { return IntegrationAggregateType.PROFIT_DISTRIBUTION.wireName(); }
        @Override
        public String aggregateId() { return String.valueOf(distributionId); }
        @Override
        public String eventType() { return IntegrationEventType.LEDGER_PROFIT_DISTRIBUTED.wireName(); }
    }
}
