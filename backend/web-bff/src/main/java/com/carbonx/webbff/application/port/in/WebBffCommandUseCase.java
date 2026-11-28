package com.carbonx.webbff.application.port.in;

import com.carbonx.common.domain.Money;
import java.time.Instant;

/**
 * Inbound CQRS Command Facade Port for Web SPA composite user actions.
 * Forwards Idempotency-Key to downstream domain saga orchestrators; holds zero business state itself.
 */
public interface WebBffCommandUseCase {

    InstantOrderReceiptView placeDesktopInstantBuyOrder(
            String idempotencyKey,
            Long buyerCompanyId,
            Long buyerUserId,
            Long listingId,
            long quantity
    );

    ProfitDistributionLaunchView triggerCompanyProfitDistribution(
            String idempotencyKey,
            Long companyId,
            Long emissionReportId,
            Long creditBatchId,
            Money totalDistributionAmount
    );

    record InstantOrderReceiptView(
            Long orderId,
            String orderCode,
            String allocatedSerialRange,
            Money debitedAmount,
            Money platformFee,
            Instant completedAt
    ) {}

    record ProfitDistributionLaunchView(
            Long distributionId,
            String batchCode,
            int targetedEvOwnersCount,
            Money totalAmount,
            String status
    ) {}
}
