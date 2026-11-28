package com.carbonx.registry.infrastructure.messaging;

import com.carbonx.common.events.IntegrationEventType;
import com.carbonx.common.events.IntegrationEvents.EmissionReportApprovedEvent;
import com.carbonx.registry.application.exception.RegistryApplicationException;
import com.carbonx.registry.application.exception.RegistryErrorCode;
import com.carbonx.registry.application.port.in.RegistryCommandUseCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code mrv.events} and mints credit batches for approved emission reports.
 *
 * <p>Delivery is at-least-once, so issuance is idempotent on {@code sourceReportId}: a replayed
 * message returns the batch that already exists instead of minting a second one. Messages that can
 * never succeed (unreadable payload, other event types, or a report with no issuable tonnage) are
 * logged and acknowledged — retrying them would only poison the partition.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MrvReportApprovedConsumer {

    private static final IntegrationEventType APPROVED_EVENT = IntegrationEventType.MRV_EMISSION_REPORT_APPROVED;

    private final ObjectMapper objectMapper;
    private final RegistryCommandUseCase registryCommandUseCase;

    @KafkaListener(topics = "${carbonx.kafka.topics.mrv-events:mrv.events}",
            groupId = "${spring.kafka.consumer.group-id:registry-service}")
    public void onMrvEvent(String payload) {
        handle(payload);
    }

    /** Extracted so the consumer contract can be tested without a broker. */
    void handle(String payload) {
        MrvEventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(payload, MrvEventEnvelope.class);
        } catch (JsonProcessingException ex) {
            log.error("Dropping unreadable mrv.events payload: {}", ex.getOriginalMessage());
            return;
        }
        if (envelope == null || !APPROVED_EVENT.matches(envelope.eventType())) {
            log.debug("Ignoring mrv event type {}", envelope == null ? null : envelope.eventType());
            return;
        }
        if (envelope.data() == null || envelope.data().isNull()) {
            log.error("Dropping {} envelope {} without a data body", APPROVED_EVENT.wireName(), envelope.eventId());
            return;
        }

        EmissionReportApprovedEvent event;
        try {
            event = objectMapper.treeToValue(envelope.data(), EmissionReportApprovedEvent.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.error("Dropping malformed {} envelope {}: {}", APPROVED_EVENT.wireName(), envelope.eventId(),
                    ex.getMessage());
            return;
        }
        if (event == null) {
            log.error("Dropping {} envelope {} whose data body is empty", APPROVED_EVENT.wireName(), envelope.eventId());
            return;
        }

        try {
            Long batchId = registryCommandUseCase.issueBatchFromApprovedReport(event);
            log.info("Approved report {} of project {} issued as batch {}", event.reportId(), event.projectId(),
                    batchId);
        } catch (RegistryApplicationException ex) {
            if (ex.code() == RegistryErrorCode.UNISSUABLE_REPORT) {
                log.error("Report {} produces no issuable credit ({}); leaving it for an administrator",
                        event.reportId(), ex.getMessage());
                return;
            }
            // Transient failures (DB unavailable, lock timeout) must be retried by the container.
            throw ex;
        }
    }
}
