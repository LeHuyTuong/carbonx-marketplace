package com.carbonx.common.domain;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    default String eventId() { return UUID.randomUUID().toString(); }
    String aggregateType();
    String aggregateId();
    String eventType();
    default Instant occurredAt() { return Instant.now(); }
}
