package com.carbonx.identity.infrastructure.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Polling cadence of the transactional-outbox relay. */
@ConfigurationProperties(prefix = "carbonx.outbox")
public record OutboxProperties(long relayIntervalMs, long relayInitialDelayMs) {

    public OutboxProperties {
        relayIntervalMs = relayIntervalMs <= 0 ? 2_000L : relayIntervalMs;
        relayInitialDelayMs = relayInitialDelayMs < 0 ? 5_000L : relayInitialDelayMs;
    }
}
