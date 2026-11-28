package com.carbonx.webbff.application.port.out;

import com.carbonx.common.domain.Money;
import com.carbonx.webbff.application.port.in.WebBffQueryUseCase;
import java.util.List;

/**
 * Outbound Downstream Microservice Ports for Web BFF.
 * Implemented in infrastructure.client via @FeignClient + Spring Cloud LoadBalancer + Resilience4j
 * (@CircuitBreaker, @Bulkhead, @Retry) with graceful fallback values when a non-critical service is degraded.
 */
public interface WebDownstreamClientsPort {

    CompanyIdentitySnapshot fetchCompanyIdentity(Long companyId);

    MrvSummarySnapshot fetchCompanyMrvSummary(Long companyId);

    RegistryPortfolioSnapshot fetchCompanyCreditPortfolio(Long companyId);

    List<WebBffQueryUseCase.RecentOrderRow> fetchRecentCompanyOrders(Long companyId, int limit);

    Money fetchWalletBalance(Long userId);

    Long forwardInstantBuyOrder(String idempotencyKey, Long buyerCompanyId, Long buyerUserId, Long listingId, long quantity);

    Long forwardProfitDistribution(String idempotencyKey, Long companyId, Long reportId, Long batchId, Money amount);

    record CompanyIdentitySnapshot(Long companyId, String companyName, String kycStatus, long linkedVehicleCount) {}

    record MrvSummarySnapshot(long pendingReports, long approvedReports) {}

    record RegistryPortfolioSnapshot(long availableCredits, long lockedCredits, long retiredCredits) {}
}
