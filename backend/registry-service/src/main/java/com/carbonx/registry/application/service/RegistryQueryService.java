package com.carbonx.registry.application.service;

import com.carbonx.registry.application.exception.RegistryApplicationException;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase;
import com.carbonx.registry.application.port.out.BatchProvenanceCachePort;
import com.carbonx.registry.application.port.out.CreditBatchRepositoryPort;
import com.carbonx.registry.application.port.out.CreditHoldingRepositoryPort;
import com.carbonx.registry.application.port.out.CreditMovementRepositoryPort;
import com.carbonx.registry.domain.credit.CreditBatch;
import com.carbonx.registry.domain.credit.CreditHolding;
import com.carbonx.registry.domain.credit.CreditMovement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Query side of the registry. Reads never lock rows: holdings are visible to their owner only and
 * batch provenance is served through Redis (db fallback) so the marketplace can show serial
 * provenance without touching the write path.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class RegistryQueryService implements RegistryQueryUseCase {

    private final CreditHoldingRepositoryPort holdingRepository;
    private final CreditMovementRepositoryPort movementRepository;
    private final CreditBatchRepositoryPort batchRepository;
    private final BatchProvenanceCachePort provenanceCache;

    @Override
    public List<HoldingView> listCompanyHoldings(Long companyId) {
        requireCompany(companyId);
        List<CreditHolding> holdings = holdingRepository.findByHolderCompanyId(companyId);
        if (holdings.isEmpty()) {
            return List.of();
        }
        List<Long> batchIds = holdings.stream().map(CreditHolding::batchId).distinct().toList();
        Map<Long, CreditBatch> batches = batchRepository.findAllByIds(batchIds).stream()
                .collect(Collectors.toMap(CreditBatch::id, Function.identity()));
        return holdings.stream()
                .map(holding -> toView(holding, batches.get(holding.batchId())))
                .toList();
    }

    @Override
    public List<MovementView> listHoldingMovements(Long companyId, Long holdingId) {
        requireCompany(companyId);
        if (holdingId == null) {
            throw RegistryApplicationException.badRequest("holdingId is required");
        }
        CreditHolding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> RegistryApplicationException.notFound("Holding not found: " + holdingId));
        if (!holding.owns(companyId)) {
            throw RegistryApplicationException.forbidden("Holding " + holdingId + " belongs to another company");
        }
        return movementRepository.findByHoldingId(holdingId).stream()
                .map(RegistryQueryService::toView)
                .toList();
    }

    @Override
    public BatchProvenanceView getBatchBySourceReportId(Long sourceReportId) {
        if (sourceReportId == null) {
            throw RegistryApplicationException.badRequest("sourceReportId is required");
        }
        Optional<BatchProvenanceView> cached = provenanceCache.get(sourceReportId);
        if (cached.isPresent()) {
            return cached.get();
        }
        CreditBatch batch = batchRepository.findBySourceReportId(sourceReportId)
                .orElseThrow(() -> RegistryApplicationException.notFound(
                        "No credit batch was issued for report " + sourceReportId));
        BatchProvenanceView view = new BatchProvenanceView(batch.id(), batch.batchCode(), batch.sourceReportId(),
                batch.serialRangeLabel(), batch.totalIssuedQty());
        putProvenance(view);
        return view;
    }

    @Override
    public List<BatchDetailView> listAllBatches() {
        return batchRepository.findAllOrderedDesc().stream()
                .map(batch -> new BatchDetailView(
                        batch.id(), batch.batchCode(), batch.sourceReportId(), batch.projectId(),
                        batch.projectTitleSnap(), batch.issuerCompanyId(), batch.vintageYear(),
                        batch.totalIssuedQty(), batch.totalTco2e(), batch.residualTco2e(),
                        batch.serialStart(), batch.serialEnd(), "ISSUED",
                        batch.issuedAt(), batch.expiresAt()))
                .toList();
    }

    @Override
    public List<CompanyBatchView> listCompanyBatches(Long companyId) {
        requireCompany(companyId);
        List<CreditHolding> holdings = holdingRepository.findByHolderCompanyId(companyId);
        if (holdings.isEmpty()) {
            return List.of();
        }
        List<Long> batchIds = holdings.stream().map(CreditHolding::batchId).distinct().toList();
        Map<Long, CreditBatch> batches = batchRepository.findAllByIds(batchIds).stream()
                .collect(Collectors.toMap(CreditBatch::id, Function.identity()));
        return holdings.stream()
                .map(holding -> {
                    CreditBatch batch = batches.get(holding.batchId());
                    String status = holding.availableQty() > 0 ? "ACTIVE"
                            : (holding.retiredQty() > 0 && holding.lockedQty() == 0 ? "RETIRED" : "LOCKED");
                    return new CompanyBatchView(
                            holding.batchId(),
                            holding.id(),
                            batch == null ? "BATCH-" + holding.batchId() : batch.batchCode(),
                            batch == null ? null : batch.projectId(),
                            batch == null ? "Carbon Batch" : batch.projectTitleSnap(),
                            batch == null ? 0 : batch.vintageYear(),
                            holding.availableQty(),
                            holding.availableQty(),
                            holding.lockedQty(),
                            holding.retiredQty(),
                            batch == null ? java.math.BigDecimal.ZERO : batch.totalTco2e(),
                            batch == null ? java.math.BigDecimal.ZERO : batch.residualTco2e(),
                            holding.serialStart(),
                            holding.serialEnd(),
                            status,
                            batch == null ? null : batch.issuedAt(),
                            batch == null ? null : batch.expiresAt());
                })
                .toList();
    }

    /** Cache writes are best effort: a Redis outage must not break a provenance read. */
    private void putProvenance(BatchProvenanceView view) {
        try {
            provenanceCache.put(view);
        } catch (RuntimeException ex) {
            log.warn("Could not cache provenance of report {}: {}", view.sourceReportId(), ex.getMessage());
        }
    }

    private static HoldingView toView(CreditHolding holding, CreditBatch batch) {
        return new HoldingView(holding.id(), holding.batchId(),
                batch == null ? null : batch.batchCode(),
                batch == null ? null : batch.projectTitleSnap(),
                holding.availableQty(), holding.lockedQty(), holding.retiredQty(),
                holding.serialRangeLabel());
    }

    private static MovementView toView(CreditMovement movement) {
        return new MovementView(movement.id(), movement.movementType().name(), movement.quantity(),
                movement.serialRangeLabel(), movement.referenceId(), movement.createdAt());
    }

    private static void requireCompany(Long companyId) {
        if (companyId == null) {
            throw RegistryApplicationException.forbidden("Company identity is required");
        }
    }
}
