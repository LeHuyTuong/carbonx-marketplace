package com.carbonx.registry.infrastructure.messaging;

import com.carbonx.common.events.IntegrationEventType;
import com.carbonx.common.events.IntegrationEvents.EmissionReportApprovedEvent;
import com.carbonx.registry.application.exception.RegistryApplicationException;
import com.carbonx.registry.application.exception.RegistryErrorCode;
import com.carbonx.registry.application.port.in.RegistryCommandUseCase;
import com.carbonx.registry.infrastructure.outbox.OutboxEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MrvReportApprovedConsumerTest {

    private static final String APPROVED = IntegrationEventType.MRV_EMISSION_REPORT_APPROVED.wireName();

    @Mock
    private RegistryCommandUseCase registryCommandUseCase;

    private ObjectMapper objectMapper;
    private MrvReportApprovedConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new MrvReportApprovedConsumer(objectMapper, registryCommandUseCase);
    }

    @Test
    @DisplayName("An approved-report envelope is unwrapped and handed to the issuance use case")
    void approvedEnvelopeIsUnwrapped() throws Exception {
        given(registryCommandUseCase.issueBatchFromApprovedReport(any())).willReturn(55L);

        consumer.handle(envelope(APPROVED, approvedEvent()));

        then(registryCommandUseCase).should().issueBatchFromApprovedReport(argThat(event ->
                event.reportId().equals(31L)
                        && event.projectId().equals(11L)
                        && event.companyId().equals(7L)
                        && "2026-09".equals(event.period())
                        && event.totalCo2Kg().compareTo(new BigDecimal("67660.0")) == 0
                        && event.bufferRate().compareTo(new BigDecimal("0.05")) == 0));
    }

    @Test
    @DisplayName("Events of other types on the same topic are ignored")
    void otherEventTypesAreIgnored() throws Exception {
        consumer.handle(envelope(IntegrationEventType.IDENTITY_VEHICLE_REGISTERED.wireName(), approvedEvent()));

        then(registryCommandUseCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("A replayed envelope is safe because issuance itself is idempotent")
    void replayIsDelegatedAndLeftToTheIdempotentService() throws Exception {
        given(registryCommandUseCase.issueBatchFromApprovedReport(any())).willReturn(55L);

        consumer.handle(envelope(APPROVED, approvedEvent()));
        consumer.handle(envelope(APPROVED, approvedEvent()));

        then(registryCommandUseCase).should(org.mockito.Mockito.times(2))
                .issueBatchFromApprovedReport(any());
    }

    @Test
    @DisplayName("A report that can never be issued is acknowledged instead of poisoning the partition")
    void unissuableReportIsAcknowledged() throws Exception {
        given(registryCommandUseCase.issueBatchFromApprovedReport(any())).willThrow(
                new RegistryApplicationException(RegistryErrorCode.UNISSUABLE_REPORT, "no credits"));

        assertThatCode(() -> consumer.handle(envelope(APPROVED, approvedEvent()))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A transient failure is rethrown so the Kafka error handler retries it")
    void transientFailureIsRethrown() throws Exception {
        given(registryCommandUseCase.issueBatchFromApprovedReport(any())).willThrow(
                new RegistryApplicationException(RegistryErrorCode.CONFLICT, "database is busy"));

        assertThatThrownBy(() -> consumer.handle(envelope(APPROVED, approvedEvent())))
                .isInstanceOf(RegistryApplicationException.class);
    }

    @Test
    @DisplayName("An unreadable payload is dropped, not retried forever")
    void malformedPayloadIsDropped() {
        consumer.handle("{not-json");

        then(registryCommandUseCase).should(never()).issueBatchFromApprovedReport(any());
    }

    @Test
    @DisplayName("An approved envelope without a data body is dropped")
    void missingDataIsDropped() throws Exception {
        consumer.handle(objectMapper.writeValueAsString(
                new OutboxEnvelope("evt-1", APPROVED, "EmissionReport", "31", Instant.now(), null)));

        then(registryCommandUseCase).should(never()).issueBatchFromApprovedReport(any());
    }

    private String envelope(String eventType, EmissionReportApprovedEvent event) throws Exception {
        return objectMapper.writeValueAsString(new OutboxEnvelope("evt-1", eventType, event.aggregateType(),
                event.aggregateId(), Instant.parse("2026-09-30T10:15:30Z"), event));
    }

    private static EmissionReportApprovedEvent approvedEvent() {
        return new EmissionReportApprovedEvent(31L, 11L, "EV fleet HCMC", 7L, "2026-09",
                new BigDecimal("100000.0000"), new BigDecimal("67660.0000"), new BigDecimal("0.6766"),
                new BigDecimal("0.05"), new BigDecimal("0.03"), new BigDecimal("0.02"));
    }
}
