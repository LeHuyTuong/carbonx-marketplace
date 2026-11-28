package com.carbonx.common.events;

import java.util.Arrays;
import java.util.Optional;

/** Canonical aggregate type names recorded in {@code outbox_events} and Kafka envelopes. */
public enum IntegrationAggregateType {
    VEHICLE("Vehicle"),
    EMISSION_REPORT("EmissionReport"),
    CREDIT_BATCH("CreditBatch"),
    TRADE_ORDER("TradeOrder"),
    ORDER_SAGA("OrderSaga"),
    PROFIT_DISTRIBUTION("ProfitDistribution");

    private final String wireName;

    IntegrationAggregateType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static Optional<IntegrationAggregateType> fromWireName(String rawAggregateType) {
        if (rawAggregateType == null || rawAggregateType.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.wireName.equals(rawAggregateType))
                .findFirst();
    }
}
