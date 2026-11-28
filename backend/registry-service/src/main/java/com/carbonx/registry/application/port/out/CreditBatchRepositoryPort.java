package com.carbonx.registry.application.port.out;

import com.carbonx.registry.domain.credit.CreditBatch;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence port of the immutable {@code credit_batches} ledger. */
public interface CreditBatchRepositoryPort {

    /** Inserts a new batch. Batches are immutable: an already persisted batch is never updated. */
    CreditBatch insert(CreditBatch batch);

    Optional<CreditBatch> findById(Long id);

    List<CreditBatch> findAllByIds(Collection<Long> ids);

    default List<CreditBatch> findAllOrderedDesc() {
        return List.of();
    }

    /** Consumer idempotency key: at most one batch per approved emission report. */
    Optional<CreditBatch> findBySourceReportId(Long sourceReportId);

    /**
     * Highest serial already allocated for the {@code (project, issuer, vintage)} scope, or {@code 0}.
     * The adapter locks the scope rows so two concurrent issuances cannot mint the same serial.
     */
    long highestAllocatedSerial(Long projectId, Long issuerCompanyId, int vintageYear);

    /** Residual tCO2e of the most recent batch of the scope — the VM0038 carry-over. */
    Optional<BigDecimal> findResidualOfLatestBatch(Long projectId, Long issuerCompanyId, int vintageYear);
}
