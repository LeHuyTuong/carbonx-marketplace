package com.carbonx.common.events;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationEventsContractTest {

    @Test
    void integrationEventTypesHaveUniqueWireNamesAndLookup() {
        long distinctCount = Arrays.stream(IntegrationEventType.values())
                .map(IntegrationEventType::wireName)
                .distinct()
                .count();
        assertEquals(IntegrationEventType.values().length, distinctCount);
        for (IntegrationEventType type : IntegrationEventType.values()) {
            assertEquals(type, IntegrationEventType.fromWireName(type.wireName()).orElseThrow());
            assertTrue(type.matches(type.wireName()));
        }
        assertTrue(IntegrationEventType.fromWireName("unknown.event").isEmpty());
    }

    @Test
    void integrationEventRecordsDelegateToCanonicalEnums() {
        var event = new IntegrationEvents.EmissionReportApprovedEvent(
                10L, 2L, "EV Fleet", 5L, "2026-10",
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals("EmissionReport", event.aggregateType());
        assertEquals("10", event.aggregateId());
        assertEquals(IntegrationEventType.MRV_EMISSION_REPORT_APPROVED.wireName(), event.eventType());
    }
}
