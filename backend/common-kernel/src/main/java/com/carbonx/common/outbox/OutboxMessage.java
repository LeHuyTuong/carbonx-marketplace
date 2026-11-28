package com.carbonx.common.outbox;

import java.time.Instant;

public record OutboxMessage(
        String eventId, String aggregateType, String aggregateId, String eventType,
        String payloadJson, String traceId, OutboxStatus status, int retryCount, Instant createdAt
) {}
