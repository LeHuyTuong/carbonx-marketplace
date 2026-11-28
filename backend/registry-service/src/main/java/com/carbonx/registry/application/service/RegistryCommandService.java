package com.carbonx.registry.application.service;

import com.carbonx.common.domain.SerialRange;
import com.carbonx.common.events.IntegrationEvents.CreditBatchIssuedEvent;
import com.carbonx.common.events.IntegrationEvents.EmissionReportApprovedEvent;
import com.carbonx.common.outbox.OutboxPort;
import com.carbonx.registry.application.exception.RegistryApplicationException;
import com.carbonx.registry.application.exception.RegistryErrorCode;
import com.carbonx.registry.application.port.in.RegistryCommandUseCase;
import com.carbonx.registry.application.port.out.BatchProvenanceCachePort;
import com.carbonx.registry.application.port.out.CreditBatchRepositoryPort;
import com.carbonx.registry.application.port.out.CreditHoldingRepositoryPort;
import com.carbonx.registry.application.port.out.CreditMovementRepositoryPort;
import com.carbonx.registry.application.port.out.RetirementCertificateRepositoryPort;
import com.carbonx.registry.application.port.out.StaticReferenceCachePort;
import com.carbonx.registry.application.port.out.StaticReferenceCachePort.ProjectMethodologyRef;
import com.carbonx.registry.domain.credit.CreditBatch;
import com.carbonx.registry.domain.credit.CreditFormula;
import com.carbonx.registry.domain.credit.CreditHolding;
import com.carbonx.registry.domain.credit.CreditMovement;
import com.carbonx.registry.domain.credit.IssuanceComputation;
import com.carbonx.registry.domain.credit.MovementType;
import com.carbonx.registry.domain.credit.RetirementCertificate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Command side of the registry CQRS split.
 *
 * <p>Issuance is driven by {@code EmissionReportApprovedEvent} on {@code mrv.events}: the batch,
 * the issuer holding, the ISSUE ledger entry, the {@code CreditBatchIssuedEvent} outbox row and the
 * cache eviction all commit in one local transaction, so a Kafka or Redis outage can never leave a
 * half-issued batch behind. Idempotency is structural — {@code credit_batches.source_report_id} is
 * unique and re-delivery simply returns the existing batch id.</p>
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class RegistryCommandService implements RegistryCommandUseCase {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final int VINTAGE_YEAR_LENGTH = 4;
    private static final int MAX_ORDER_REFERENCE_LENGTH = 128;
    private static final Duration BATCH_VALIDITY = Duration.ofDays(365);

    private final CreditBatchRepositoryPort batchRepository;
    private final CreditHoldingRepositoryPort holdingRepository;
    private final CreditMovementRepositoryPort movementRepository;
    private final RetirementCertificateRepositoryPort certificateRepository;
    private final BatchProvenanceCachePort provenanceCache;
    private final StaticReferenceCachePort staticReferenceCache;
    private final OutboxPort outbox;
    private final CreditFormula creditFormula = new CreditFormula();

    @Override
    public Long issueBatchFromApprovedReport(EmissionReportApprovedEvent event) {
        if (event == null) {
            throw RegistryApplicationException.badRequest("EmissionReportApprovedEvent is required");
        }
        requireId(event.reportId(), "reportId");
        requireId(event.projectId(), "projectId");
        requireId(event.companyId(), "companyId");

        Optional<CreditBatch> alreadyIssued = batchRepository.findBySourceReportId(event.reportId());
        if (alreadyIssued.isPresent()) {
            log.info("Ignoring duplicate approval of report {}: batch {} was already issued",
                    event.reportId(), alreadyIssued.get().id());
            return alreadyIssued.get().id();
        }

        int vintageYear = vintageYear(event.period());
        BigDecimal carriedResidual = batchRepository
                .findResidualOfLatestBatch(event.projectId(), event.companyId(), vintageYear)
                .orElse(BigDecimal.ZERO);
        IssuanceComputation computation = creditFormula.compute(event.totalEnergyKwh(), event.totalCo2Kg(),
                event.emissionFactor(), event.bufferRate(), event.uncertaintyRate(), event.leakageRate(),
                carriedResidual);
        if (!computation.isIssuable()) {
            throw new RegistryApplicationException(RegistryErrorCode.UNISSUABLE_REPORT,
                    "Report " + event.reportId() + " yields no issuable credit (net " + computation.netTco2e()
                            + " tCO2e after VM0038 deductions); an administrator must review it");
        }

        long serialStart = batchRepository
                .highestAllocatedSerial(event.projectId(), event.companyId(), vintageYear) + 1;
        SerialRange serials = new SerialRange(serialStart, Math.addExact(serialStart, computation.credits() - 1));
        String prefix = serialPrefix(vintageYear, event.projectId(), event.reportId());
        String batchCode = prefix + String.format("%06d_%06d", serials.start(), serials.end());
        Instant issuedAt = Instant.now();

        CreditBatch batch = batchRepository.insert(CreditBatch.issue(batchCode, event.reportId(),
                event.projectId(), event.projectTitle(), event.companyId(), vintageYear, computation.netTco2e(),
                computation.residualTco2e(), prefix, serials, issuedAt, issuedAt.plus(BATCH_VALIDITY)));

        CreditHolding holding = holdingRepository.save(CreditHolding.issue(batch.id(), event.companyId(),
                computation.credits(), serials.start()));
        movementRepository.save(CreditMovement.of(holding, MovementType.ISSUE, computation.credits(), serials,
                "report-" + event.reportId()));
        outbox.append(new CreditBatchIssuedEvent(batch.id(), batch.batchCode(), event.reportId(),
                event.companyId(), batch.totalIssuedQty(), batch.serialStart(), batch.serialEnd()));
        evictProvenance(event.reportId());
        rememberMethodology(event);

        log.info("Issued batch {} for report {}: {} credits on serials {}, net {} tCO2e, residual {} tCO2e",
                batch.batchCode(), event.reportId(), computation.credits(), serials.formatted(prefix),
                computation.netTco2e(), computation.residualTco2e());
        return batch.id();
    }

    @Override
    public LockResult lockHoldingCredits(String idempotencyKey, Long holdingId, Long sellerCompanyId,
                                         long quantity, String listingRef) {
        String key = requireKey(idempotencyKey);
        requireId(holdingId, "holdingId");
        requireId(sellerCompanyId, "sellerCompanyId");

        CreditHolding holding = holdingRepository.findByIdForUpdate(holdingId)
                .orElseThrow(() -> RegistryApplicationException.notFound("Holding not found: " + holdingId));
        if (!holding.owns(sellerCompanyId)) {
            throw RegistryApplicationException.forbidden(
                    "Holding " + holdingId + " belongs to another company");
        }

        Optional<CreditMovement> replay = movementRepository.findFirstByReferenceIdAndType(key, MovementType.LOCK);
        if (replay.isPresent()) {
            guardReplayHolding(replay.get(), holdingId, key);
            log.info("Idempotent replay of lock {} on holding {}: {} credits already locked",
                    key, holdingId, replay.get().quantity());
            return lockResult(holding, replay.get().quantity());
        }

        CreditHolding locked = holding.lock(quantity);
        // The locked block grows at its tail, so the newly reserved serials follow the previous ones.
        SerialRange lockedRange = new SerialRange(holding.serialStart() + holding.lockedQty(),
                holding.serialStart() + holding.lockedQty() + quantity - 1);
        CreditHolding saved = holdingRepository.save(locked);
        movementRepository.save(CreditMovement.of(saved, MovementType.LOCK, quantity, lockedRange, key));
        log.info("Locked {} credits on holding {} (serials {}) for listing {} key={}", quantity, holdingId,
                lockedRange.formatted(""), listingRef, key);
        return lockResult(saved, quantity);
    }

    @Override
    public void compensateReleaseLockedCredits(String idempotencyKey, Long holdingId, long quantity, String reason) {
        String key = requireKey(idempotencyKey);
        requireId(holdingId, "holdingId");

        CreditHolding holding = holdingRepository.findByIdForUpdate(holdingId)
                .orElseThrow(() -> RegistryApplicationException.notFound("Holding not found: " + holdingId));

        Optional<CreditMovement> replay = movementRepository.findFirstByReferenceIdAndType(key, MovementType.RELEASE);
        if (replay.isPresent()) {
            guardReplayHolding(replay.get(), holdingId, key);
            log.info("Ignoring duplicate release {} on holding {}: credits were already released", key, holdingId);
            return;
        }

        CreditHolding released = holding.release(quantity);
        SerialRange releasedRange = new SerialRange(
                holding.serialStart() + holding.lockedQty() - quantity,
                holding.serialStart() + holding.lockedQty() - 1);
        CreditHolding saved = holdingRepository.save(released);
        movementRepository.save(CreditMovement.of(saved, MovementType.RELEASE, quantity, releasedRange, key));
        log.info("Released {} locked credits back to holding {} (serials {}) key={} reason={}",
                quantity, holdingId, releasedRange.formatted(""), key, reason);
    }

    @Override
    public TransferResult transferLockedCredits(String idempotencyKey, Long sellerHoldingId, Long buyerCompanyId,
                                                long quantity, String orderRef) {
        String key = requireKey(idempotencyKey);
        requireId(sellerHoldingId, "sellerHoldingId");
        requireId(buyerCompanyId, "buyerCompanyId");

        Optional<CreditMovement> replay = movementRepository
                .findFirstByReferenceIdAndType(key, MovementType.TRANSFER_IN);
        if (replay.isPresent()) {
            CreditMovement received = replay.get();
            log.info("Idempotent replay of transfer {}: buyer holding {} already received serials {}",
                    key, received.holdingId(), received.serialRangeLabel());
            return new TransferResult(received.holdingId(), received.batchId(), received.serialStart(),
                    received.serialEnd());
        }

        CreditHolding seller = holdingRepository.findByIdForUpdate(sellerHoldingId)
                .orElseThrow(() -> RegistryApplicationException.notFound("Holding not found: " + sellerHoldingId));
        CreditHolding sellerAfter = seller.transferLocked(quantity);
        SerialRange moved = new SerialRange(seller.serialStart(), seller.serialStart() + quantity - 1);

        CreditHolding savedSeller = holdingRepository.save(sellerAfter);
        CreditHolding buyer = holdingRepository.save(
                CreditHolding.issue(seller.batchId(), buyerCompanyId, quantity, moved.start()));
        movementRepository.save(CreditMovement.of(savedSeller, MovementType.TRANSFER_OUT, quantity, moved, key));
        movementRepository.save(CreditMovement.of(buyer, MovementType.TRANSFER_IN, quantity, moved, key));

        log.info("Transferred {} credits from holding {} to buyer company {} in holding {} (serials {}) order={}",
                quantity, sellerHoldingId, buyerCompanyId, buyer.id(), moved.formatted(""),
                truncate(orderRef, MAX_ORDER_REFERENCE_LENGTH));
        return new TransferResult(buyer.id(), buyer.batchId(), moved.start(), moved.end());
    }

    @Override
    public Long retireCredits(Long companyId, Long holdingId, long quantity, String beneficiaryName, String reason) {
        requireId(companyId, "companyId");
        requireId(holdingId, "holdingId");

        CreditHolding holding = holdingRepository.findByIdForUpdate(holdingId)
                .orElseThrow(() -> RegistryApplicationException.notFound("Holding not found: " + holdingId));
        if (!holding.owns(companyId)) {
            throw RegistryApplicationException.forbidden(
                    "Holding " + holdingId + " belongs to another company");
        }

        SerialRange range = holding.retirementRange(quantity);
        CreditHolding retired = holdingRepository.save(holding.retire(quantity));
        movementRepository.save(CreditMovement.of(retired, MovementType.RETIRE, quantity, range,
                "retire-" + holdingId + "-" + range.start()));

        CreditBatch batch = batchRepository.findById(holding.batchId())
                .orElseThrow(() -> RegistryApplicationException.notFound(
                        "Batch not found: " + holding.batchId()));
        String certificateCode = certificateCode(batch.batchCode(), range.start());
        RetirementCertificate certificate = certificateRepository.save(RetirementCertificate.issue(
                certificateCode, retired, quantity, range.start(), range.end(), beneficiaryName, reason));

        log.info("Retired {} credits from holding {} (serials {}) certificate {} beneficiary={} reason={}",
                quantity, holdingId, range.formatted(""), certificateCode, beneficiaryName, reason);
        return certificate.id();
    }

    private LockResult lockResult(CreditHolding holding, long lockedQty) {
        CreditBatch batch = batchRepository.findById(holding.batchId())
                .orElseThrow(() -> RegistryApplicationException.notFound("Batch not found: " + holding.batchId()));
        return new LockResult(holding.id(), batch.id(), batch.batchCode(), batch.projectTitleSnap(), lockedQty);
    }

    private static void guardReplayHolding(CreditMovement movement, Long holdingId, String key) {
        if (!holdingId.equals(movement.holdingId())) {
            throw RegistryApplicationException.conflict(
                    "Idempotency key " + key + " was already used for another holding");
        }
    }

    /** A failed cache eviction must never roll back an issuance that is already committed in the DB. */
    private void evictProvenance(Long sourceReportId) {
        try {
            provenanceCache.evict(sourceReportId);
        } catch (RuntimeException ex) {
            log.warn("Could not evict provenance cache for report {}: {}", sourceReportId, ex.getMessage());
        }
    }

    private void rememberMethodology(EmissionReportApprovedEvent event) {
        try {
            staticReferenceCache.putProjectMethodology(new ProjectMethodologyRef(event.projectId(),
                    event.projectTitle(), event.emissionFactor(), event.bufferRate(),
                    event.uncertaintyRate(), event.leakageRate()));
        } catch (RuntimeException ex) {
            log.warn("Could not cache methodology of project {}: {}", event.projectId(), ex.getMessage());
        }
    }

    private static int vintageYear(String period) {
        if (period == null || period.trim().length() < VINTAGE_YEAR_LENGTH) {
            throw RegistryApplicationException.badRequest(
                    "Reporting period must start with the vintage year, e.g. 2026-09");
        }
        String year = period.trim().substring(0, VINTAGE_YEAR_LENGTH);
        try {
            int parsed = Integer.parseInt(year);
            if (parsed < 1900 || parsed > 9999) {
                throw RegistryApplicationException.badRequest("Invalid vintage year in period " + period);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw RegistryApplicationException.badRequest("Invalid vintage year in period " + period);
        }
    }

    private static String serialPrefix(int vintageYear, Long projectId, Long reportId) {
        return vintageYear + "-PRJ" + projectId + "-RPT" + reportId + "-";
    }

    private static String certificateCode(String batchCode, long serialStart) {
        return "CERT-" + batchCode + "-" + String.format("%06d", serialStart);
    }

    private static String requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw RegistryApplicationException.badRequest("idempotencyKey is required");
        }
        String key = idempotencyKey.trim();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw RegistryApplicationException.badRequest(
                    "idempotencyKey must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return key;
    }

    private static void requireId(Long value, String field) {
        if (value == null) {
            throw RegistryApplicationException.badRequest(field + " is required");
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
