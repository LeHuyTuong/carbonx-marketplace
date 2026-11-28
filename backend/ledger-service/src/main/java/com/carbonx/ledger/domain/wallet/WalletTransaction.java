package com.carbonx.ledger.domain.wallet;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletTransaction(
        Long id,
        Long walletId,
        Long userId,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Long orderRef,
        String idempotencyKey,
        String description,
        Instant createdAt
) {
    public static WalletTransaction record(Wallet wallet, TransactionType type, BigDecimal amount,
                                           Long orderRef, String idempotencyKey, String description) {
        return new WalletTransaction(null, wallet.id(), wallet.userId(), type, amount, wallet.balance(),
                orderRef, idempotencyKey, description, Instant.now());
    }
}
