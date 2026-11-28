package com.carbonx.ledger.application.port.out;

import com.carbonx.ledger.domain.wallet.WalletTransaction;

import java.util.List;
import java.util.Optional;

public interface WalletTransactionRepositoryPort {
    Optional<WalletTransaction> findById(Long id);
    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);
    List<WalletTransaction> findByOrderRef(Long orderRef);
    List<WalletTransaction> findByUserIdOrdered(Long userId);
    List<WalletTransaction> findAllOrdered();
    long countAll();
    WalletTransaction save(WalletTransaction tx);
}
