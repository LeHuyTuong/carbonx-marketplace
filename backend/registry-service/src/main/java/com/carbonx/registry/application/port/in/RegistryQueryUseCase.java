package com.carbonx.registry.application.port.in;

import java.time.Instant;
import java.util.List;

public interface RegistryQueryUseCase {
    List<HoldingView> listCompanyHoldings(Long companyId);
    List<MovementView> listHoldingMovements(Long companyId, Long holdingId);
    BatchProvenanceView getBatchBySourceReportId(Long sourceReportId);

    record HoldingView(Long holdingId, Long batchId, String batchCode, String projectTitle, long availableQty, long lockedQty, long retiredQty, String serialRange) {}
    record MovementView(Long id, String movementType, long quantity, String serialRange, String referenceId, Instant createdAt) {}
    record BatchProvenanceView(Long batchId, String batchCode, Long sourceReportId, String serialRange, long totalIssuedQty) {}
}
