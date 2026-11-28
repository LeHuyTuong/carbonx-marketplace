package com.carbonx.ledger.application.port.in;

import com.carbonx.shared.domain.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Inbound Use-Case Port: Company Profit Sharing to EV Owners with async claim_token retry & Excel export. */
public interface ProfitSharingUseCase {
    DistributionPreviewView previewDistribution(Long companyId, Long sourceReportId);

    DistributionView executeDistribution(Long companyId, Long sourceReportId, String idempotencyKey);

    DistributionView retryFailedPayouts(Long companyId, Long distributionId);

    List<DistributionView> listMyDistributions(Long companyId);

    byte[] exportDistributionExcel(Long companyId, Long distributionId);

    record DistributionPreviewView(
            Long sourceReportId,
            Long creditBatchId,
            String batchCode,
            String period,
            Money unitPricePerCredit,
            Money totalBudget,
            List<PayoutRowView> rows
    ) {}

    record PayoutRowView(Long evOwnerId, String ownerEmail, int vehicleCount, BigDecimal kwhContributed, BigDecimal allocatedCredits, Money amountUsd, String status) {}

    record DistributionView(Long id, Long sourceReportId, Long creditBatchId, String batchCode, String period, Money totalPayoutUsd, int failedCount, String status, Instant createdAt) {}
}
