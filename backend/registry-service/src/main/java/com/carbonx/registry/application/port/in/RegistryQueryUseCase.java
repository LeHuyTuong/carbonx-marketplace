package com.carbonx.registry.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface RegistryQueryUseCase {
    List<HoldingView> listCompanyHoldings(Long companyId);
    List<MovementView> listHoldingMovements(Long companyId, Long holdingId);
    BatchProvenanceView getBatchBySourceReportId(Long sourceReportId);
    default List<BatchDetailView> listAllBatches() {
        return List.of();
    }
    default List<CompanyBatchView> listCompanyBatches(Long companyId) {
        return List.of();
    }

    record HoldingView(Long holdingId, Long batchId, String batchCode, String projectTitle, long availableQty, long lockedQty, long retiredQty, String serialRange) {}
    record MovementView(Long id, String movementType, long quantity, String serialRange, String referenceId, Instant createdAt) {}
    record BatchProvenanceView(Long batchId, String batchCode, Long sourceReportId, String serialRange, long totalIssuedQty) {}
    record BatchDetailView(Long id, String batchCode, Long reportId, Long projectId, String projectTitle, Long issuerCompanyId, int vintageYear, long creditsCount, BigDecimal totalTco2e, BigDecimal residualTco2e, long serialFrom, long serialTo, String status, Instant issuedAt, Instant expiresAt) {}
    record CompanyBatchView(Long id, Long holdingId, String batchCode, Long projectId, String projectTitle, int vintageYear, long creditsCount, long availableQty, long lockedQty, long retiredQty, BigDecimal totalTco2e, BigDecimal residualTco2e, long serialFrom, long serialTo, String status, Instant issuedAt, Instant expiresAt) {}
}
