package com.carbonx.common.events;

import java.util.Arrays;
import java.util.Optional;

/** Canonical wire names for cross-service integration events published through the outbox and Kafka. */
public enum IntegrationEventType {
    IDENTITY_VEHICLE_REGISTERED("identity.vehicle.registered"),
    MRV_EMISSION_REPORT_APPROVED("mrv.emission-report.approved"),
    REGISTRY_CREDIT_BATCH_ISSUED("registry.credit-batch.issued"),
    TRADING_ORDER_SETTLED("trading.order.settled"),
    TRADING_ORDER_COMPENSATED("trading.order.compensated"),
    LEDGER_PROFIT_DISTRIBUTED("ledger.profit.distributed");

    private final String wireName;

    IntegrationEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public boolean matches(String rawEventType) {
        return wireName.equals(rawEventType);
    }

    public static Optional<IntegrationEventType> fromWireName(String rawEventType) {
        if (rawEventType == null || rawEventType.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.wireName.equals(rawEventType))
                .findFirst();
    }
}
