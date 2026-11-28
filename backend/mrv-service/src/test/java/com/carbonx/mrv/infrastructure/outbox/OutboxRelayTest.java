package com.carbonx.mrv.infrastructure.outbox;

import com.carbonx.common.events.IntegrationAggregateType;
import com.carbonx.common.events.IntegrationEventType;
import com.carbonx.common.outbox.EventBusPublisherPort;
import com.carbonx.common.outbox.OutboxMessage;
import com.carbonx.common.outbox.OutboxStatus;
import com.carbonx.mrv.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.carbonx.mrv.infrastructure.persistence.repository.OutboxEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventJpaRepository repository;

    @Mock
    private EventBusPublisherPort eventBusPublisher;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, eventBusPublisher, new OutboxProperties("mrv.events", 1_000, 1_000));
    }

    @Test
    @DisplayName("A pending event is sent to the mrv.events topic and marked SENT")
    void relaysToMrvEventsTopic() {
        OutboxEventJpaEntity event = event();
        given(repository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING.name()))
                .willReturn(List.of(event));

        relay.relayPendingEvents();

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        then(eventBusPublisher).should().publishToKafka(topicCaptor.capture(), any(OutboxMessage.class));
        assertThat(topicCaptor.getValue()).isEqualTo("mrv.events");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT.name());
        assertThat(event.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("A broker failure increments the retry counter and keeps the row PENDING")
    void brokerFailureKeepsPending() {
        OutboxEventJpaEntity event = event();
        given(repository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING.name()))
                .willReturn(List.of(event));
        doThrow(new IllegalStateException("broker down"))
                .when(eventBusPublisher).publishToKafka(anyString(), any(OutboxMessage.class));

        relay.relayPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING.name());
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("broker down");
    }

    @Test
    @DisplayName("The fifth failure moves the event to DEAD_LETTER for the operations team")
    void fifthFailureDeadLetters() {
        OutboxEventJpaEntity event = event();
        given(repository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING.name()))
                .willReturn(List.of(event));
        doThrow(new IllegalStateException("broker down"))
                .when(eventBusPublisher).publishToKafka(anyString(), any(OutboxMessage.class));

        for (int attempt = 1; attempt <= 5; attempt++) {
            relay.relayPendingEvents();
        }

        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER.name());
        then(eventBusPublisher).should(org.mockito.Mockito.times(5))
                .publishToKafka(eq("mrv.events"), any(OutboxMessage.class));
    }

    private static OutboxEventJpaEntity event() {
        return OutboxEventJpaEntity.pending("evt-1", IntegrationAggregateType.EMISSION_REPORT.wireName(), "31",
                IntegrationEventType.MRV_EMISSION_REPORT_APPROVED.wireName(),
                "{\"eventId\":\"evt-1\"}", "trace-1");
    }
}
