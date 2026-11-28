package com.carbonx.trading.domain.saga;

/**
 * Standard 6-state Saga Orchestration lifecycle.
 * Replaces blocking 2PC/XA protocols with non-blocking local ACID steps + compensating rollbacks.
 */
public enum OrderSagaState {
    PENDING,
    EXECUTING,
    COMPLETED,
    FAILED,
    COMPENSATING,
    COMPENSATED;

    public boolean canTransitionTo(OrderSagaState next) {
        return switch (this) {
            case PENDING -> next == EXECUTING || next == FAILED;
            case EXECUTING -> next == COMPLETED || next == FAILED;
            case FAILED -> next == COMPENSATING;
            case COMPENSATING -> next == COMPENSATED || next == FAILED;
            case COMPLETED, COMPENSATED -> false;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPENSATED;
    }
}
