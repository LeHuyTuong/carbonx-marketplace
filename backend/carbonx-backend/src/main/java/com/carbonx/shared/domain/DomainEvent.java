package com.carbonx.shared.domain;

import java.time.Instant;
import java.util.UUID;

/** Marker contract for immutable Domain Events persisted via Transactional Outbox. */
public interface DomainEvent {
    default String eventId() {
        return UUID.randomUUID().toString();
    }

    String aggregateType();

    String aggregateId();

    String eventType();

    default Instant occurredAt() {
        return Instant.now();
    }
}
