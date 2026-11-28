package com.carbonx.ledger.api;

import com.carbonx.shared.domain.Money;

/**
 * Public Settlement Port exposed by Ledger Bounded Context.
 * Enforces ordered wallet locking (FOR UPDATE), idempotency key, and immutable WalletTransaction rows.
 */
public interface LedgerSettlementPort {
    void settleTradeOrder(
            String idempotencyKey,
            Long orderId,
            Long buyerUserId,
            Long sellerUserId,
            Money grossAmount,
            Money platformFee
    );
}
