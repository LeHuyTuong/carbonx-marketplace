package com.carbonx.ledger.application.port.in;

import com.carbonx.common.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;

/** Guarantees full EV Owner payout traceability back to CreditBatch ID, batchCode, serialRange & sourceReportId. */
public interface LedgerQueryUseCase {
    PayoutProvenanceView getTransactionProvenance(Long userId, Long walletTransactionId);
    byte[] exportDistributionExcel(Long companyId, Long distributionId);

    record PayoutProvenanceView(Long walletTransactionId, Long distributionId, Long sourceReportId, Long creditBatchId, String batchCode, String serialRange, String period, BigDecimal kwhContributed, BigDecimal allocatedCredits, Money amountUsd, Instant paidAt) {}
}
