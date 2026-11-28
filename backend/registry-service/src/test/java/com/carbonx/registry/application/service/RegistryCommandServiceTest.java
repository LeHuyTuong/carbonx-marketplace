package com.carbonx.registry.application.service;

import com.carbonx.common.domain.SerialRange;
import com.carbonx.common.events.IntegrationEvents.CreditBatchIssuedEvent;
import com.carbonx.common.events.IntegrationEvents.EmissionReportApprovedEvent;
import com.carbonx.registry.application.exception.RegistryApplicationException;
import com.carbonx.registry.application.exception.RegistryErrorCode;
import com.carbonx.registry.application.port.in.RegistryCommandUseCase.LockResult;
import com.carbonx.registry.application.port.in.RegistryCommandUseCase.TransferResult;
import com.carbonx.registry.domain.credit.CreditBatch;
import com.carbonx.registry.domain.credit.CreditHolding;
import com.carbonx.registry.domain.credit.CreditMovement;
import com.carbonx.registry.domain.credit.MovementType;
import com.carbonx.registry.domain.credit.RetirementCertificate;
import com.carbonx.registry.domain.shared.InsufficientCreditsException;
import com.carbonx.registry.support.InMemoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistryCommandServiceTest {

    private static final Long COMPANY = 7L;
    private static final Long BUYER = 200L;
    private static final Long PROJECT = 11L;

    private InMemoryRegistry registry;
    private RegistryCommandService service;

    @BeforeEach
    void setUp() {
        registry = new InMemoryRegistry();
        service = new RegistryCommandService(registry.batchPort, registry.holdingPort, registry.movementPort,
                registry.certificatePort, registry.provenancePort, registry.staticReferencePort,
                registry.outboxPort);
    }

    @Test
    @DisplayName("An approved report mints the batch, the issuer holding, the ISSUE ledger entry and the outbox event")
    void issuesBatchHoldingLedgerAndEvent() {
        Long batchId = service.issueBatchFromApprovedReport(approvedEvent(31L));

        assertThat(batchId).isEqualTo(1L);
        CreditBatch batch = registry.batch(batchId);
        assertThat(batch.totalIssuedQty()).isEqualTo(60);
        assertThat(batch.serialStart()).isEqualTo(1);
        assertThat(batch.serialEnd()).isEqualTo(60);
        assertThat(batch.totalTco2e()).isEqualByComparingTo("60.894");
        assertThat(batch.residualTco2e()).isEqualByComparingTo("0.894");
        assertThat(batch.vintageYear()).isEqualTo(2026);
        assertThat(batch.batchCode()).isEqualTo("2026-PRJ11-RPT31-000001_000060");

        CreditHolding holding = registry.holding(1L);
        assertThat(holding.batchId()).isEqualTo(batchId);
        assertThat(holding.holderCompanyId()).isEqualTo(COMPANY);
        assertThat(holding.availableQty()).isEqualTo(60);
        assertThat(holding.serialRangeLabel()).isEqualTo("000001..000060");

        assertThat(registry.movements()).singleElement().satisfies(movement -> {
            assertThat(movement.movementType()).isEqualTo(MovementType.ISSUE);
            assertThat(movement.quantity()).isEqualTo(60);
            assertThat(movement.serialStart()).isEqualTo(1);
            assertThat(movement.serialEnd()).isEqualTo(60);
        });

        assertThat(registry.outboxEvents()).singleElement().isInstanceOfSatisfying(CreditBatchIssuedEvent.class,
                event -> {
                    assertThat(event.batchId()).isEqualTo(batchId);
                    assertThat(event.sourceReportId()).isEqualTo(31L);
                    assertThat(event.totalIssuedQty()).isEqualTo(60);
                    assertThat(event.serialStart()).isEqualTo(1);
                    assertThat(event.serialEnd()).isEqualTo(60);
                });

        assertThat(registry.methodologies()).containsKey(PROJECT);
    }

    @Test
    @DisplayName("Re-delivering the same approval is idempotent: no second batch, holding or event")
    void duplicateApprovalIsIgnored() {
        Long first = service.issueBatchFromApprovedReport(approvedEvent(31L));
        Long replay = service.issueBatchFromApprovedReport(approvedEvent(31L));

        assertThat(replay).isEqualTo(first);
        assertThat(registry.allBatches()).hasSize(1);
        assertThat(registry.allHoldings()).hasSize(1);
        assertThat(registry.movements()).hasSize(1);
        assertThat(registry.outboxEvents()).hasSize(1);
    }

    @Test
    @DisplayName("The next report of the same project continues the serial range and the residual")
    void continuesSerialsAndResidualWithinTheScope() {
        service.issueBatchFromApprovedReport(approvedEvent(31L));
        Long secondId = service.issueBatchFromApprovedReport(approvedEvent(32L));

        CreditBatch second = registry.batch(secondId);
        assertThat(second.serialStart()).isEqualTo(61);
        // 60.894 tCO2e of the new period + 0.894 carried over = 61.788 -> 61 credits.
        assertThat(second.totalIssuedQty()).isEqualTo(61);
        assertThat(second.serialEnd()).isEqualTo(121);
        assertThat(second.residualTco2e()).isEqualByComparingTo("0.788");
    }

    @Test
    @DisplayName("A report without issuable tonnage is rejected instead of minting an empty batch")
    void unissuableReportIsRejected() {
        EmissionReportApprovedEvent tiny = new EmissionReportApprovedEvent(33L, PROJECT, "EV fleet HCMC", COMPANY,
                "2026-09", new BigDecimal("500"), new BigDecimal("500"), new BigDecimal("1.0"),
                new BigDecimal("0.05"), new BigDecimal("0.03"), new BigDecimal("0.02"));

        assertThatThrownBy(() -> service.issueBatchFromApprovedReport(tiny))
                .isInstanceOfSatisfying(RegistryApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(RegistryErrorCode.UNISSUABLE_REPORT));
        assertThat(registry.allBatches()).isEmpty();
        assertThat(registry.outboxEvents()).isEmpty();
    }

    @Test
    @DisplayName("100 issued -> lock 80 -> transfer 80 -> retire 20 realises the 80/20 split")
    void realisesTheEightyTwentySplit() {
        Long batchId = service.issueBatchFromApprovedReport(hundredCreditEvent(31L));
        CreditHolding issued = registry.holding(holdingIdOf(batchId));

        LockResult lock = service.lockHoldingCredits("listing-1", issued.id(), COMPANY, 80, "LISTING-1");
        assertThat(lock.lockedQty()).isEqualTo(80);
        assertThat(lock.batchCode()).isEqualTo("2026-PRJ11-RPT31-000001_000100");

        CreditHolding locked = registry.holding(issued.id());
        assertThat(locked.availableQty()).isEqualTo(20);
        assertThat(locked.lockedSerialRange()).isEqualTo(new SerialRange(1, 80));
        assertThat(locked.availableSerialRange()).isEqualTo(new SerialRange(81, 100));

        TransferResult transfer = service.transferLockedCredits("order-1", issued.id(), BUYER, 80, "ORDER-1");
        assertThat(transfer.serialStart()).isEqualTo(1);
        assertThat(transfer.serialEnd()).isEqualTo(80);

        CreditHolding buyer = registry.holding(transfer.buyerHoldingId());
        assertThat(buyer.holderCompanyId()).isEqualTo(BUYER);
        assertThat(buyer.availableQty()).isEqualTo(80);
        assertThat(buyer.serialRangeLabel()).isEqualTo("000001..000080");

        CreditHolding seller = registry.holding(issued.id());
        assertThat(seller.availableQty()).isEqualTo(20);
        assertThat(seller.lockedQty()).isZero();
        assertThat(seller.serialRangeLabel()).isEqualTo("000081..000100");

        Long certificateId = service.retireCredits(COMPANY, issued.id(), 20, "VinFast", "Scope 1 offset");
        RetirementCertificate certificate = registry.certificate(certificateId);
        assertThat(certificate.retiredQty()).isEqualTo(20);
        assertThat(certificate.serialStart()).isEqualTo(81);
        assertThat(certificate.serialEnd()).isEqualTo(100);
        assertThat(certificate.certificateCode()).startsWith("CERT-2026-PRJ11-RPT31-000001_000100-");

        CreditHolding retired = registry.holding(issued.id());
        assertThat(retired.availableQty()).isZero();
        assertThat(retired.retiredQty()).isEqualTo(20);

        assertThat(registry.movementsOf(issued.id()))
                .extracting(CreditMovement::movementType)
                .containsExactly(MovementType.ISSUE, MovementType.LOCK, MovementType.TRANSFER_OUT, MovementType.RETIRE);
    }

    @Test
    @DisplayName("Locking twice with the same idempotency key never doubles the reservation")
    void lockIsIdempotent() {
        Long holdingId = holdingIdOf(service.issueBatchFromApprovedReport(hundredCreditEvent(31L)));

        service.lockHoldingCredits("listing-1", holdingId, COMPANY, 80, "LISTING-1");
        LockResult replay = service.lockHoldingCredits("listing-1", holdingId, COMPANY, 80, "LISTING-1");

        assertThat(replay.lockedQty()).isEqualTo(80);
        assertThat(registry.holding(holdingId).lockedQty()).isEqualTo(80);
        assertThat(registry.movementsOf(holdingId))
                .filteredOn(movement -> movement.movementType() == MovementType.LOCK)
                .hasSize(1);
    }

    @Test
    @DisplayName("Transferring twice with the same saga key returns the same buyer holding")
    void transferIsIdempotent() {
        Long holdingId = holdingIdOf(service.issueBatchFromApprovedReport(hundredCreditEvent(31L)));
        service.lockHoldingCredits("listing-1", holdingId, COMPANY, 80, "LISTING-1");

        TransferResult first = service.transferLockedCredits("order-1", holdingId, BUYER, 80, "ORDER-1");
        TransferResult replay = service.transferLockedCredits("order-1", holdingId, BUYER, 80, "ORDER-1");

        assertThat(replay.buyerHoldingId()).isEqualTo(first.buyerHoldingId());
        assertThat(replay.serialStart()).isEqualTo(1);
        assertThat(replay.serialEnd()).isEqualTo(80);
        assertThat(registry.allHoldings()).hasSize(2);
        assertThat(registry.movements())
                .filteredOn(movement -> movement.movementType() == MovementType.TRANSFER_IN)
                .hasSize(1);
    }

    @Test
    @DisplayName("A compensated saga releases the reservation exactly once")
    void compensationReleasesTheReservation() {
        Long holdingId = holdingIdOf(service.issueBatchFromApprovedReport(hundredCreditEvent(31L)));
        service.lockHoldingCredits("listing-1", holdingId, COMPANY, 80, "LISTING-1");

        service.compensateReleaseLockedCredits("order-1-compensate", holdingId, 80, "payment declined");
        service.compensateReleaseLockedCredits("order-1-compensate", holdingId, 80, "payment declined");

        CreditHolding holding = registry.holding(holdingId);
        assertThat(holding.availableQty()).isEqualTo(100);
        assertThat(holding.lockedQty()).isZero();
        assertThat(registry.movementsOf(holdingId))
                .filteredOn(movement -> movement.movementType() == MovementType.RELEASE)
                .singleElement()
                .satisfies(movement -> assertThat(movement.serialStart()).isEqualTo(1));
    }

    @Test
    @DisplayName("A company cannot lock another company's holding")
    void lockRejectsForeignCompany() {
        Long holdingId = holdingIdOf(service.issueBatchFromApprovedReport(hundredCreditEvent(31L)));

        assertThatThrownBy(() -> service.lockHoldingCredits("listing-1", holdingId, 999L, 10, "LISTING-1"))
                .isInstanceOfSatisfying(RegistryApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(RegistryErrorCode.FORBIDDEN));
        assertThat(registry.holding(holdingId).lockedQty()).isZero();
    }

    @Test
    @DisplayName("A holding can never be overdrawn")
    void lockBeyondAvailableIsRejected() {
        Long holdingId = holdingIdOf(service.issueBatchFromApprovedReport(hundredCreditEvent(31L)));

        assertThatThrownBy(() -> service.lockHoldingCredits("listing-1", holdingId, COMPANY, 101, "LISTING-1"))
                .isInstanceOf(InsufficientCreditsException.class);
    }

    @Test
    @DisplayName("Reusing one idempotency key on another holding is a conflict, not a silent no-op")
    void idempotencyKeyReuseAcrossHoldingsIsRejected() {
        Long firstHolding = holdingIdOf(service.issueBatchFromApprovedReport(hundredCreditEvent(31L)));
        Long secondHolding = holdingIdOf(service.issueBatchFromApprovedReport(hundredCreditEvent(32L)));

        service.lockHoldingCredits("listing-1", firstHolding, COMPANY, 10, "LISTING-1");

        assertThatThrownBy(() -> service.lockHoldingCredits("listing-1", secondHolding, COMPANY, 10, "LISTING-2"))
                .isInstanceOfSatisfying(RegistryApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(RegistryErrorCode.CONFLICT));
    }

    @Test
    @DisplayName("A company cannot retire another company's credits")
    void retireRejectsForeignCompany() {
        Long holdingId = holdingIdOf(service.issueBatchFromApprovedReport(hundredCreditEvent(31L)));

        assertThatThrownBy(() -> service.retireCredits(999L, holdingId, 10, "Someone", "not mine"))
                .isInstanceOfSatisfying(RegistryApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(RegistryErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("A reporting period without a vintage year is rejected before anything is written")
    void invalidPeriodIsRejected() {
        EmissionReportApprovedEvent malformed = new EmissionReportApprovedEvent(34L, PROJECT, "EV fleet HCMC",
                COMPANY, "Q3", new BigDecimal("100000"), new BigDecimal("100000"), BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatThrownBy(() -> service.issueBatchFromApprovedReport(malformed))
                .isInstanceOfSatisfying(RegistryApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(RegistryErrorCode.BAD_REQUEST));
        assertThat(registry.allBatches()).isEmpty();
    }

    private Long holdingIdOf(Long batchId) {
        return registry.allHoldings().stream()
                .filter(holding -> holding.batchId().equals(batchId))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static EmissionReportApprovedEvent approvedEvent(Long reportId) {
        return new EmissionReportApprovedEvent(reportId, PROJECT, "EV fleet HCMC", COMPANY, "2026-09",
                new BigDecimal("100000.0000"), new BigDecimal("67660.0000"), new BigDecimal("0.6766"),
                new BigDecimal("0.05"), new BigDecimal("0.03"), new BigDecimal("0.02"));
    }

    /** 100 000 kg CO2e with no deductions mints exactly 100 credits. */
    private static EmissionReportApprovedEvent hundredCreditEvent(Long reportId) {
        return new EmissionReportApprovedEvent(reportId, PROJECT, "EV fleet HCMC", COMPANY, "2026-09",
                new BigDecimal("100000"), new BigDecimal("100000"), BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
