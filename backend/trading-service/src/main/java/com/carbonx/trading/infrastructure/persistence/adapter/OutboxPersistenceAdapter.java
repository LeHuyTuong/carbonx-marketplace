package com.carbonx.trading.infrastructure.persistence.adapter;

import com.carbonx.common.domain.DomainEvent;
import com.carbonx.common.outbox.OutboxPort;
import com.carbonx.trading.infrastructure.outbox.OutboxEnvelope;
import com.carbonx.trading.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.carbonx.trading.infrastructure.persistence.repository.OutboxEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Appends domain events to {@code outbox_events} inside the caller's local transaction. */
@Component
@RequiredArgsConstructor
public class OutboxPersistenceAdapter implements OutboxPort {

    private final OutboxEventJpaRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    public void append(DomainEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(OutboxEnvelope.of(event));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialise domain event " + event.eventType(), ex);
        }
        repository.save(OutboxEventJpaEntity.pending(event.eventId(), event.aggregateType(), event.aggregateId(),
                event.eventType(), payload, MDC.get("traceId")));
    }
}
