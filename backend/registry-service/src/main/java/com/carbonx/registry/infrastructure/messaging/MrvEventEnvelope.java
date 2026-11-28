package com.carbonx.registry.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Envelope written by the MRV outbox: metadata plus the event body. Unknown fields are ignored so
 * a producer can add metadata without breaking this consumer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MrvEventEnvelope(String eventId, String eventType, String aggregateType, String aggregateId,
                               Instant occurredAt, JsonNode data) {
}
