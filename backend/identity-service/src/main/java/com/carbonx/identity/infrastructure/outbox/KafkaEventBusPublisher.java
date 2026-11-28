package com.carbonx.identity.infrastructure.outbox;

import com.carbonx.common.outbox.EventBusPublisherPort;
import com.carbonx.common.outbox.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Publishes outbox messages to Kafka and surfaces broker failures to the relay. */
@Component
@RequiredArgsConstructor
public class KafkaEventBusPublisher implements EventBusPublisherPort {

    private static final long SEND_TIMEOUT_SECONDS = 5L;

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Override
    public void publishToKafka(String topic, OutboxMessage message) {
        try {
            kafkaTemplate.send(topic, message.aggregateId(), message.payloadJson())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted publishing outbox event " + message.eventId(), ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Kafka publish failed for outbox event " + message.eventId(), ex);
        }
    }
}
