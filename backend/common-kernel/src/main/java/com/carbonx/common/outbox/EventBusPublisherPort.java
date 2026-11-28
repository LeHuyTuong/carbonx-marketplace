package com.carbonx.common.outbox;

/** Publishes Outbox messages to Apache Kafka topics. */
public interface EventBusPublisherPort {
    void publishToKafka(String topic, OutboxMessage message);
}
