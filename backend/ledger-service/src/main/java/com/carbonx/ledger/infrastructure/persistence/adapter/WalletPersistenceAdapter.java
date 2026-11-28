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
        return repository.findByUserId(userId).map(WalletPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Wallet> findByUserIdForUpdate(Long userId) {
        return repository.findByUserIdForUpdate(userId).map(WalletPersistenceAdapter::toDomain);
    }

    @Override
    public Wallet save(Wallet wallet) {
        WalletJpaEntity saved = repository.save(WalletJpaEntity.builder()
                .id(wallet.id())
                .userId(wallet.userId())
                .balance(wallet.balance())
                .currency(wallet.currency())
                .version(wallet.version())
                .createdAt(wallet.createdAt())
                .updatedAt(wallet.updatedAt())
                .build());
        return toDomain(saved);
    }

    private static Wallet toDomain(WalletJpaEntity entity) {
        return new Wallet(entity.getId(), entity.getUserId(), entity.getBalance(),
                entity.getCurrency(), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
