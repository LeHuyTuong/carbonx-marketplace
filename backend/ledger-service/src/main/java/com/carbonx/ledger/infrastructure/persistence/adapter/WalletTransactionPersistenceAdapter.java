package com.carbonx.ledger.infrastructure.persistence.adapter;

import com.carbonx.ledger.application.port.out.WalletTransactionRepositoryPort;
import com.carbonx.ledger.domain.wallet.TransactionType;
import com.carbonx.ledger.domain.wallet.WalletTransaction;
import com.carbonx.ledger.infrastructure.persistence.entity.WalletTransactionJpaEntity;
import com.carbonx.ledger.infrastructure.persistence.repository.WalletTransactionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WalletTransactionPersistenceAdapter implements WalletTransactionRepositoryPort {

    private final WalletTransactionJpaRepository repository;

    @Override
    public Optional<WalletTransaction> findById(Long id) {
        return repository.findById(id).map(WalletTransactionPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(WalletTransactionPersistenceAdapter::toDomain);
    }

    @Override
    public List<WalletTransaction> findByOrderRef(Long orderRef) {
        return repository.findByOrderRefOrderByIdAsc(orderRef).stream()
                .map(WalletTransactionPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<WalletTransaction> findByUserIdOrdered(Long userId) {
        return repository.findByUserIdOrderByIdDesc(userId).stream()
                .map(WalletTransactionPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<WalletTransaction> findAllOrdered() {
        return repository.findAllByOrderByIdDesc().stream()
                .map(WalletTransactionPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public long countAll() {
        return repository.count();
    }

    @Override
    public WalletTransaction save(WalletTransaction tx) {
        WalletTransactionJpaEntity saved = repository.save(WalletTransactionJpaEntity.builder()
                .id(tx.id())
                .walletId(tx.walletId())
                .userId(tx.userId())
                .txType(tx.type().name())
                .amount(tx.amount())
                .balanceAfter(tx.balanceAfter())
                .orderRef(tx.orderRef())
                .idempotencyKey(tx.idempotencyKey())
                .description(tx.description())
                .createdAt(tx.createdAt())
                .build());
        return toDomain(saved);
    }

    private static WalletTransaction toDomain(WalletTransactionJpaEntity e) {
        return new WalletTransaction(e.getId(), e.getWalletId(), e.getUserId(),
                TransactionType.valueOf(e.getTxType()), e.getAmount(), e.getBalanceAfter(),
                e.getOrderRef(), e.getIdempotencyKey(), e.getDescription(), e.getCreatedAt());
    }
}
