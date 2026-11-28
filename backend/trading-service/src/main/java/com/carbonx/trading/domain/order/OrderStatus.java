package com.carbonx.trading.domain.order;

/** Lifecycle of a trade order: PENDING while the saga runs, then FINISHED or CANCELLED. */
public enum OrderStatus {
    PENDING,
    FINISHED,
    CANCELLED
}
