package com.carbonx.trading.infrastructure.outbox;

import com.carbonx.common.outbox.EventBusPublisherPort;
import com.carbonx.common.outbox.OutboxStatus;
import com.carbonx.trading.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.carbonx.trading.infrastructure.persistence.repository.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Relays PENDING outbox rows to the {@code trading.events} topic. After five failed attempts a row
 * moves to DEAD_LETTER for the operations team instead of blocking the relay forever.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    static final int MAX_RETRY_COUNT = 5;

    private final OutboxEventJpaRepository repository;
    private final EventBusPublisherPort eventBusPublisher;
    private final OutboxProperties properties;

    /** Invoked by {@link OutboxRelayScheduler} so the transaction proxy is always applied. */
    @Transactional
    public void relayPendingEvents() {
        List<OutboxEventJpaEntity> pending =
                repository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING.name());
        for (OutboxEventJpaEntity event : pending) {
            try {
                eventBusPublisher.publishToKafka(properties.topic(), event.toMessage());
                event.markSent();
            } catch (RuntimeException ex) {
                event.markFailed(ex.getMessage(), MAX_RETRY_COUNT);
                log.warn("Outbox relay failed eventId={} eventType={} topic={} retryCount={} status={}",
                        event.getEventId(), event.getEventType(), properties.topic(), event.getRetryCount(),
                        event.getStatus());
            }
        }
    }
}
