package com.carbonx.shared.outbox;

public enum OutboxStatus {
    PENDING,
    SENT,
    DEAD_LETTER
}
