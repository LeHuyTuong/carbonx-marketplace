package com.carbonx.shared.outbox;

import java.time.Instant;

/** Immutable representation of a persisted Outbox event record. */
public record OutboxMessage(
        String eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payloadJson,
        String traceId,
        OutboxStatus status,
        int retryCount,
        Instant createdAt
) {}
