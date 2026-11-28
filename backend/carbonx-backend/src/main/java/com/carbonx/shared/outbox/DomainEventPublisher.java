package com.carbonx.shared.outbox;

/**
 * Outbound Port for dispatching outbox events.
 * Phase 1: In-process Spring ApplicationEventPublisher adapter.
 * Phase 2: Apache Kafka (KRaft) Producer adapter.
 */
public interface DomainEventPublisher {
    void publish(OutboxMessage message);
}
