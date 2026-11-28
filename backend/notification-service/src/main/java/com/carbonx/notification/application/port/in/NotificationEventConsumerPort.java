package com.carbonx.notification.application.port.in;

import com.carbonx.common.outbox.OutboxMessage;

/** Idempotent Kafka Event Consumer Port pushing SSE & Email notifications. */
public interface NotificationEventConsumerPort {
    void onIntegrationEvent(OutboxMessage message);
}
