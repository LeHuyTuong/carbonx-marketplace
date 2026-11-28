package com.carbonx.registry.application.port.in;

import com.carbonx.common.events.IntegrationEvents.EmissionReportApprovedEvent;

/** Solves 100 issued -> lock 80 -> transfer 80 to buyer holding, keep 20 in seller holding for retirement. */
public interface RegistryCommandUseCase {
    Long issueBatchFromApprovedReport(EmissionReportApprovedEvent event);
    LockResult lockHoldingCredits(String idempotencyKey, Long holdingId, Long sellerCompanyId, long quantity, String listingRef);
    void compensateReleaseLockedCredits(String idempotencyKey, Long holdingId, long quantity, String reason);
    TransferResult transferLockedCredits(String idempotencyKey, Long sellerHoldingId, Long buyerCompanyId, long quantity, String orderRef);
    Long retireCredits(Long companyId, Long holdingId, long quantity, String beneficiaryName, String reason);

    record LockResult(Long holdingId, Long batchId, String batchCode, String projectTitle, long lockedQty) {}
    record TransferResult(Long buyerHoldingId, Long batchId, long serialStart, long serialEnd) {}
}
