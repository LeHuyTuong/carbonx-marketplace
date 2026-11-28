package com.carbonx.registry.application.service;

import com.carbonx.common.events.IntegrationEvents.EmissionReportApprovedEvent;
import com.carbonx.registry.application.exception.RegistryApplicationException;
import com.carbonx.registry.application.exception.RegistryErrorCode;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase.BatchProvenanceView;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase.HoldingView;
import com.carbonx.registry.support.InMemoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistryQueryServiceTest {

    private static final Long COMPANY = 7L;
    private static final Long OTHER_COMPANY = 200L;

    private InMemoryRegistry registry;
    private RegistryCommandService commandService;
    private RegistryQueryService queryService;
    private Long holdingId;
    private Long batchId;

    @BeforeEach
    void setUp() {
        registry = new InMemoryRegistry();
        commandService = new RegistryCommandService(registry.batchPort, registry.holdingPort, registry.movementPort,
                registry.certificatePort, registry.provenancePort, registry.staticReferencePort,
                registry.outboxPort);
        queryService = new RegistryQueryService(registry.holdingPort, registry.movementPort, registry.batchPort,
                registry.provenancePort);
        batchId = commandService.issueBatchFromApprovedReport(new EmissionReportApprovedEvent(31L, 11L,
                "EV fleet HCMC", COMPANY, "2026-09", new BigDecimal("100000"), new BigDecimal("100000"),
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        holdingId = registry.allHoldings().iterator().next().id();
    }

    @Test
    @DisplayName("A company sees its holdings enriched with the immutable batch snapshot")
    void listCompanyHoldingsEnrichesWithBatchSnapshot() {
        List<HoldingView> holdings = queryService.listCompanyHoldings(COMPANY);

        assertThat(holdings).singleElement().satisfies(view -> {
            assertThat(view.holdingId()).isEqualTo(holdingId);
            assertThat(view.batchId()).isEqualTo(batchId);
            assertThat(view.batchCode()).isEqualTo("2026-PRJ11-RPT31-000001_000100");
            assertThat(view.projectTitle()).isEqualTo("EV fleet HCMC");
            assertThat(view.availableQty()).isEqualTo(100);
            assertThat(view.lockedQty()).isZero();
            assertThat(view.serialRange()).isEqualTo("000001..000100");
        });
    }

    @Test
    @DisplayName("Another company has no holdings and cannot read someone else's ledger")
    void otherCompanySeesNothing() {
        assertThat(queryService.listCompanyHoldings(OTHER_COMPANY)).isEmpty();

        assertThatThrownBy(() -> queryService.listHoldingMovements(OTHER_COMPANY, holdingId))
                .isInstanceOfSatisfying(RegistryApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(RegistryErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("The owner reads the append-only ledger of its holding")
    void ownerReadsTheLedger() {
        assertThat(queryService.listHoldingMovements(COMPANY, holdingId))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.movementType()).isEqualTo("ISSUE");
                    assertThat(view.quantity()).isEqualTo(100);
                    assertThat(view.serialRange()).isEqualTo("000001..000100");
                });
    }

    @Test
    @DisplayName("Provenance is read through to the database once, then served from the cache")
    void provenanceIsCachedAfterTheFirstRead() {
        int lookupsBeforeQuery = registry.sourceReportLookups();

        BatchProvenanceView first = queryService.getBatchBySourceReportId(31L);

        assertThat(first.batchId()).isEqualTo(batchId);
        assertThat(first.serialRange()).isEqualTo("000001..000100");
        assertThat(first.totalIssuedQty()).isEqualTo(100);
        assertThat(registry.cachedProvenance()).containsKey(31L);
        assertThat(registry.sourceReportLookups()).isEqualTo(lookupsBeforeQuery + 1);

        BatchProvenanceView second = queryService.getBatchBySourceReportId(31L);

        assertThat(second).isEqualTo(first);
        assertThat(registry.sourceReportLookups()).isEqualTo(lookupsBeforeQuery + 1);
    }

    @Test
    @DisplayName("A report without a batch is reported as not found")
    void unknownReportIsNotFound() {
        assertThatThrownBy(() -> queryService.getBatchBySourceReportId(999L))
                .isInstanceOfSatisfying(RegistryApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(RegistryErrorCode.NOT_FOUND));
    }
}
