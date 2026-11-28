package com.carbonx.identity.infrastructure.outbox;

import com.carbonx.common.events.IntegrationAggregateType;
import com.carbonx.common.events.IntegrationEventType;
import com.carbonx.common.outbox.EventBusPublisherPort;
import com.carbonx.common.outbox.OutboxMessage;
import com.carbonx.common.outbox.OutboxStatus;
import com.carbonx.identity.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.carbonx.identity.infrastructure.persistence.repository.OutboxEventJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final String TOPIC = IntegrationEventType.IDENTITY_VEHICLE_REGISTERED.wireName();

    @Mock
    private OutboxEventJpaRepository repository;
    @Mock
    private EventBusPublisherPort eventBusPublisher;

    @InjectMocks
    private OutboxRelay relay;

    @Test
    @DisplayName("a published event is marked SENT and sent to the topic named after its event type")
    void marksEventSentAfterPublish() {
        OutboxEventJpaEntity event = pendingEvent();
        given(repository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING.name()))
                .willReturn(List.of(event));

        relay.relayPendingEvents();

        ArgumentCaptor<OutboxMessage> published = ArgumentCaptor.forClass(OutboxMessage.class);
        then(eventBusPublisher).should().publishToKafka(eq(TOPIC), published.capture());
        assertThat(published.getValue().eventId()).isEqualTo("event-1");
        assertThat(published.getValue().aggregateId()).isEqualTo("1");
        assertThat(published.getValue().payloadJson()).contains("identity.vehicle.registered");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT.name());
        assertThat(event.getProcessedAt()).isNotNull();
        assertThat(event.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("a failing publish keeps the event PENDING and records the retry")
    void keepsEventPendingWhenPublishFails() {
        OutboxEventJpaEntity event = pendingEvent();
        given(repository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING.name()))
                .willReturn(List.of(event));
        willThrow(new IllegalStateException("Kafka publish failed"))
                .given(eventBusPublisher).publishToKafka(eq(TOPIC), any());

        relay.relayPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING.name());
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("Kafka publish failed");
    }

    @Test
    @DisplayName("after five failed attempts the event becomes a DEAD_LETTER")
    void movesEventToDeadLetterAfterFiveFailures() {
        OutboxEventJpaEntity event = pendingEvent();
        given(repository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING.name()))
                .willReturn(List.of(event));
        willThrow(new IllegalStateException("broker unavailable"))
                .given(eventBusPublisher).publishToKafka(eq(TOPIC), any());

        for (int attempt = 0; attempt < 5; attempt++) {
            relay.relayPendingEvents();
        }

        then(eventBusPublisher).should(times(5)).publishToKafka(eq(TOPIC), any());
        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER.name());
    }

    private static OutboxEventJpaEntity pendingEvent() {
        return OutboxEventJpaEntity.pending("event-1", IntegrationAggregateType.VEHICLE.wireName(), "1", TOPIC,
                "{\"eventType\":\"" + TOPIC + "\"}", "trace-1");
    }
}
