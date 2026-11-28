package com.carbonx.trading.domain.saga;

/** Ordered execution steps coordinated by the Order Saga Orchestrator. */
public enum OrderSagaStep {
    RESERVE_LISTING,
    SETTLE_PAYMENT,
    TRANSFER_CREDITS,
    FINALIZE_ORDER
}
