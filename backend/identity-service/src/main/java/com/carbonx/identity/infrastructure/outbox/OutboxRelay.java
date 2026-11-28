package com.carbonx.identity.infrastructure.outbox;

import com.carbonx.common.outbox.EventBusPublisherPort;
import com.carbonx.common.outbox.OutboxStatus;
import com.carbonx.identity.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.carbonx.identity.infrastructure.persistence.repository.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Relays PENDING outbox rows to Kafka. Each event is published to a topic named after its
 * event type; after five failed attempts the row moves to DEAD_LETTER for the operations team.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxEventJpaRepository repository;
    private final EventBusPublisherPort eventBusPublisher;

    /** Invoked by {@link OutboxRelayScheduler} so the transaction proxy is always applied. */
    @Transactional
    public void relayPendingEvents() {
        List<OutboxEventJpaEntity> pending =
                repository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING.name());
        for (OutboxEventJpaEntity event : pending) {
            try {
                eventBusPublisher.publishToKafka(event.getEventType(), event.toMessage());
                event.markSent();
            } catch (RuntimeException ex) {
                event.markFailed(ex.getMessage(), MAX_RETRY_COUNT);
                log.warn("Outbox relay failed eventId={} eventType={} retryCount={} status={}",
                        event.getEventId(), event.getEventType(), event.getRetryCount(), event.getStatus());
            }
        }
    }
}
