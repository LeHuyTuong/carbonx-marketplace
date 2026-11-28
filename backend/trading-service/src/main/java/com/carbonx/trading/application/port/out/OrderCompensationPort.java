package com.carbonx.trading.application.port.out;

/**
 * Backward Compensating Transactions Port executed in reverse step order during FAILED -> COMPENSATING -> COMPENSATED.
 * Mandatory Contract for every method:
 * 1. Idempotent (safe to replay via idempotencyKey under network retries)
 * 2. Reversible (restores wallet balance, locked holding quota, listing availability & order status CANCELLED)
 * 3. Reliable (guarded by Resilience4j Retry + Outbox Dead-Letter escalation so rollbacks never silently drop).
 */
public interface OrderCompensationPort {

    void compensateCancelOrderAndReleaseListing(String idempotencyKey, Long orderId, Long listingId, long quantity, String reason);

    void compensateRefundWalletSettlement(String idempotencyKey, Long orderId, String reason);

    void compensateReleaseLockedCredits(String idempotencyKey, Long holdingId, long quantity, String reason);
}
