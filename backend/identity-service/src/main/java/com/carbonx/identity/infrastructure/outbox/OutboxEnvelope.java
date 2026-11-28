package com.carbonx.identity.infrastructure.outbox;

import com.carbonx.common.domain.DomainEvent;

import java.time.Instant;

/** Stable Kafka payload: envelope metadata plus the integration event body. */
public record OutboxEnvelope(String eventId, String eventType, String aggregateType, String aggregateId,
                             Instant occurredAt, Object data) {

    public static OutboxEnvelope of(DomainEvent event) {
        return new OutboxEnvelope(event.eventId(), event.eventType(), event.aggregateType(),
                event.aggregateId(), event.occurredAt(), event);
    }
}
