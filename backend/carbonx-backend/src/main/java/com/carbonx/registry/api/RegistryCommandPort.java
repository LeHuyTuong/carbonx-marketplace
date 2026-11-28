package com.carbonx.registry.api;

/**
 * Public Command Port exposed by Carbon Registry Bounded Context.
 * Handles partial batch splits (e.g., 100 issued -> lock/transfer 80 to buyer holding, keep 20 in seller holding).
 */
public interface RegistryCommandPort {
    HoldingSnapshot lockCreditsForListing(Long holdingId, Long sellerCompanyId, long quantity, String listingRef);

    void releaseLockedCredits(Long holdingId, long quantity, String listingRef);

    TransferResult transferSoldCredits(Long sellerHoldingId, Long buyerCompanyId, long quantity, String orderRef);

    record HoldingSnapshot(Long holdingId, Long batchId, String batchCode, String projectTitle, long availableQty, long lockedQty) {}

    record TransferResult(Long buyerHoldingId, Long batchId, long serialStart, long serialEnd) {}
}
