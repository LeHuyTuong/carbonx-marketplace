package com.carbonx.webbff.application.port.out;

import com.carbonx.common.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * CQRS Read Projection / Reporting Port avoiding cross-service SQL JOINs across 6 isolated DBs.
 * Fed asynchronously by Apache Kafka domain events (CreditBatchIssuedEvent, OrderSettledEvent,
 * OrderCompensatedEvent, ProfitDistributedEvent) to power Admin ESG Analytics & Audit exports.
 */
public interface CarbonAnalyticsProjectionPort {

    PlatformEsgSummaryProjection getPlatformEsgSummary();

    List<BatchAuditTimelineProjection> listBatchLifecycleReport(int page, int size);

    record PlatformEsgSummaryProjection(
            long totalBatchesIssued,
            long totalCreditsMinted,
            long totalCreditsRetired,
            BigDecimal totalCo2AbatedTonnes,
            Money cumulativeGmv,
            Money cumulativeEvPayouts,
            long completedSagasCount,
            long compensatedSagasCount,
            Instant lastEventProjectedAt
    ) {}

    record BatchAuditTimelineProjection(
            Long batchId,
            String batchCode,
            Long sourceReportId,
            String companyName,
            long issuedQty,
            long soldQty,
            long retiredQty,
            Money distributedToEvOwnersUsd,
            Instant issuedAt
    ) {}
}
