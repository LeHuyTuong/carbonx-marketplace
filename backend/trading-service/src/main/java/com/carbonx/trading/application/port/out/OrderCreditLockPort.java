package com.carbonx.trading.application.port.out;

/**
 * Forward registry calls of the Order Saga: lock the seller's credits before money moves and
 * release the reservation when the saga compensates. Both are idempotent on the saga key.
 */
public interface OrderCreditLockPort {

    void lockCreditsForListing(String idempotencyKey, Long holdingId, Long sellerCompanyId, long quantity,
                               String listingRef);

    void releaseLockedCredits(String idempotencyKey, Long holdingId, long quantity, String reason);
}
