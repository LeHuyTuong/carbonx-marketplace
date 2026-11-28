package com.carbonx.registry.infrastructure.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Polling cadence of the outbox relay and the Kafka topic it publishes to. */
@ConfigurationProperties(prefix = "carbonx.outbox")
public record OutboxProperties(String topic, long relayIntervalMs, long relayInitialDelayMs) {

    public static final String DEFAULT_TOPIC = "registry.events";

    public OutboxProperties {
        topic = topic == null || topic.isBlank() ? DEFAULT_TOPIC : topic.trim();
        relayIntervalMs = relayIntervalMs <= 0 ? 2_000L : relayIntervalMs;
        relayInitialDelayMs = relayInitialDelayMs < 0 ? 5_000L : relayInitialDelayMs;
    }
}
