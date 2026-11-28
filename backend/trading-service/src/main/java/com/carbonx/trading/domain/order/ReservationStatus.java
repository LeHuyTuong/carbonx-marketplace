package com.carbonx.trading.domain.order;

/** Reservation of listing quantity while the Order Saga is in flight. */
public enum ReservationStatus {
    RESERVED,
    COMMITTED,
    CANCELLED
}
