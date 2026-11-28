package com.carbonx.ledger.application.port.out;

import com.carbonx.ledger.domain.wallet.Wallet;

import java.util.Optional;

public interface WalletRepositoryPort {
    Optional<Wallet> findByUserId(Long userId);
    Optional<Wallet> findByUserIdForUpdate(Long userId);
    Wallet save(Wallet wallet);
}
