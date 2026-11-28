package com.carbonx.webbff.application.port.in;

import com.carbonx.common.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Inbound CQRS Query Port for Web SPA (Admin, CVA, Company desktop dashboards & rich data tables).
 * Aggregates multiple microservices in parallel with Resilience4j graceful degradation (partialData=true).
 */
public interface WebBffQueryUseCase {

    CompanyWorkspaceOverview getCompanyWorkspaceOverview(Long companyId, Long userId);

    CvaAuditWorkbenchView getCvaAuditWorkbench(Long cvaUserId, int page, int size);

    AdminControlTowerView getAdminControlTowerOverview();

    MarketplaceCatalogPageView browseMarketplaceCatalog(String methodologyFilter, int page, int size);

    record CompanyWorkspaceOverview(
            Long companyId,
            String companyName,
            String kycStatus,
            long linkedVehicleCount,
            long pendingReportCount,
            long availableCreditsToSellOrRetire,
            long lockedCreditsOnMarket,
            long retiredCreditsTotal,
            Money walletBalance,
            List<RecentOrderRow> recentOrders,
            boolean partialData
    ) {}

    record RecentOrderRow(
            Long orderId,
            String orderCode,
            String batchCode,
            long quantity,
            Money totalPaid,
            String status,
            Instant settledAt
    ) {}

    record CvaAuditWorkbenchView(
            long pendingApplicationsCount,
            long pendingEmissionReportsCount,
            List<CvaReportQueueItem> reportQueue,
            boolean aiScoringAvailable,
            boolean partialData
    ) {}

    record CvaReportQueueItem(
            Long reportId,
            Long companyId,
            String companyName,
            String reportingPeriod,
            BigDecimal totalKwh,
            BigDecimal estimatedTco2e,
            Integer aiPreScore,
            int passedFraudRulesCount,
            Instant submittedAt
    ) {}

    record AdminControlTowerView(
            long totalMintedCredits,
            long totalRetiredCredits,
            long level2IssuanceQueueCount,
            long pendingWithdrawalsCount,
            Money totalMarketplaceGmv,
            Money totalPlatformFeeCollected,
            boolean partialData
    ) {}

    record MarketplaceCatalogPageView(
            List<EnrichedListingItem> items,
            int page,
            int size,
            long totalElements,
            boolean partialData
    ) {}

    record EnrichedListingItem(
            Long listingId,
            Long sellerCompanyId,
            String sellerCompanyName,
            String projectTitle,
            String methodologyCode,
            String batchCode,
            String serialRangePreview,
            long availableQuantity,
            Money pricePerCredit,
            Instant expiresAt
    ) {}
}
