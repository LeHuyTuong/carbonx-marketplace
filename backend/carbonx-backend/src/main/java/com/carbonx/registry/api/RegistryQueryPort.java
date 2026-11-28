package com.carbonx.registry.api;

import java.util.Optional;

/** Public Query Port exposed by Carbon Registry for provenance lookups (e.g. by Ledger/Trading). */
public interface RegistryQueryPort {
    Optional<BatchSummary> findBatchBySourceReportId(Long sourceReportId);

    Optional<BatchSummary> findBatchById(Long batchId);

    record BatchSummary(Long batchId, String batchCode, Long sourceReportId, String projectTitle, long totalIssuedQty, String serialPrefix, long serialStart, long serialEnd) {}
}
