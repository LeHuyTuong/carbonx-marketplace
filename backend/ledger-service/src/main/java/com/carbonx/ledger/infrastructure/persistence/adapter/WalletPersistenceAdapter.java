package com.carbonx.ledger.infrastructure.persistence.adapter;

import com.carbonx.ledger.application.port.out.WalletRepositoryPort;
import com.carbonx.ledger.domain.wallet.Wallet;
import com.carbonx.ledger.infrastructure.persistence.entity.WalletJpaEntity;
import com.carbonx.ledger.infrastructure.persistence.repository.WalletJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WalletPersistenceAdapter implements WalletRepositoryPort {

    private final WalletJpaRepository repository;

    @Override
    public Optional<Wallet> findByUserId(Long userId) {
        return repository.findByOwnerUserId(userId).map(WalletPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Wallet> findByUserIdForUpdate(Long userId) {
        return repository.findByOwnerUserIdForUpdate(userId).map(WalletPersistenceAdapter::toDomain);
    }

    @Override
    public Wallet save(Wallet wallet) {
        WalletJpaEntity saved = repository.save(WalletJpaEntity.builder()
                .id(wallet.id())
                .ownerUserId(wallet.userId())
                .balanceUsd(wallet.balance())
                .version(wallet.version())
                .createdAt(wallet.createdAt())
                .updatedAt(wallet.updatedAt())
                .build());
        return toDomain(saved);
    }

    private static Wallet toDomain(WalletJpaEntity entity) {
        return new Wallet(entity.getId(), entity.getOwnerUserId(), entity.getBalanceUsd(),
                Wallet.DEFAULT_CURRENCY, entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
