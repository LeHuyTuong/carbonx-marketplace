package com.carbonx.registry.infrastructure.outbox;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Drives the outbox relay on a dedicated daemon thread. The relay must never share a scheduler with
 * Spring Cloud Bus/Kafka binder retries: a broker outage can park that pool for minutes and would
 * silently starve event delivery.
 */
@Component
@Slf4j
public class OutboxRelayScheduler implements DisposableBean {

    private static final String THREAD_NAME = "registry-outbox-relay";

    private final OutboxRelay outboxRelay;
    private final OutboxProperties properties;
    private final ScheduledExecutorService executor;

    public OutboxRelayScheduler(OutboxRelay outboxRelay, OutboxProperties properties) {
        this.outboxRelay = outboxRelay;
        this.properties = properties;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
    }

    @PostConstruct
    void start() {
        executor.scheduleWithFixedDelay(this::runOnce, properties.relayInitialDelayMs(),
                properties.relayIntervalMs(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    void runOnce() {
        try {
            outboxRelay.relayPendingEvents();
        } catch (RuntimeException ex) {
            // A fixed-delay task is cancelled by the executor when it throws; a failed tick must
            // never escape, or outbox delivery would stop for good.
            log.error("Outbox relay tick failed: {}", ex.getMessage());
        }
    }
}
