package com.carbonx.ledger.infrastructure.persistence.adapter;

import com.carbonx.ledger.application.port.out.WalletTransactionRepositoryPort;
import com.carbonx.ledger.domain.wallet.TransactionType;
import com.carbonx.ledger.domain.wallet.WalletTransaction;
import com.carbonx.ledger.infrastructure.persistence.entity.WalletJpaEntity;
import com.carbonx.ledger.infrastructure.persistence.entity.WalletTransactionJpaEntity;
import com.carbonx.ledger.infrastructure.persistence.repository.WalletJpaRepository;
import com.carbonx.ledger.infrastructure.persistence.repository.WalletTransactionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WalletTransactionPersistenceAdapter implements WalletTransactionRepositoryPort {

    private static final String REF_ORDER = "ORDER";

    private final WalletTransactionJpaRepository repository;
    private final WalletJpaRepository walletRepository;

    @Override
    public Optional<WalletTransaction> findById(Long id) {
        return repository.findById(id).map(e -> toDomain(e, ownerOf(e.getWalletId())));
    }

    @Override
    public Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey)
                .map(e -> toDomain(e, ownerOf(e.getWalletId())));
    }

    @Override
    public List<WalletTransaction> findByOrderRef(Long orderRef) {
        if (orderRef == null) {
            return List.of();
        }
        List<WalletTransactionJpaEntity> rows = repository.findByReferenceTypeAndReferenceIdOrderByIdAsc(
                REF_ORDER, String.valueOf(orderRef));
        Map<Long, Long> owners = ownersByWalletIds(rows);
        return rows.stream().map(e -> toDomain(e, owners.get(e.getWalletId()))).toList();
    }

    @Override
    public List<WalletTransaction> findByUserIdOrdered(Long userId) {
        return walletRepository.findByOwnerUserId(userId)
                .map(wallet -> repository.findByWalletIdOrderByIdDesc(wallet.getId()).stream()
                        .map(e -> toDomain(e, userId))
                        .toList())
                .orElse(List.of());
    }

    @Override
    public List<WalletTransaction> findAllOrdered() {
        List<WalletTransactionJpaEntity> rows = repository.findAllByOrderByIdDesc();
        Map<Long, Long> owners = ownersByWalletIds(rows);
        return rows.stream().map(e -> toDomain(e, owners.get(e.getWalletId()))).toList();
    }

    @Override
    public long countAll() {
        return repository.count();
    }

    @Override
    public WalletTransaction save(WalletTransaction tx) {
        BigDecimal balanceBefore = computeBalanceBefore(tx.type(), tx.balanceAfter(), tx.amount());
        WalletTransactionJpaEntity saved = repository.save(WalletTransactionJpaEntity.builder()
                .id(tx.id())
                .walletId(tx.walletId())
                .idempotencyKey(tx.idempotencyKey())
                .txType(tx.type().name())
                .amountUsd(tx.amount())
                .balanceBefore(balanceBefore)
                .balanceAfter(tx.balanceAfter())
                .referenceType(tx.orderRef() != null ? REF_ORDER : null)
                .referenceId(tx.orderRef() != null ? String.valueOf(tx.orderRef()) : null)
                .description(tx.description())
                .createdAt(tx.createdAt())
                .build());
        return toDomain(saved, tx.userId());
    }

    private Long ownerOf(Long walletId) {
        return walletRepository.findById(walletId).map(WalletJpaEntity::getOwnerUserId).orElse(null);
    }

    private Map<Long, Long> ownersByWalletIds(List<WalletTransactionJpaEntity> rows) {
        List<Long> walletIds = rows.stream().map(WalletTransactionJpaEntity::getWalletId).distinct().toList();
        if (walletIds.isEmpty()) {
            return Map.of();
        }
        return walletRepository.findAllById(walletIds).stream()
                .collect(Collectors.toMap(WalletJpaEntity::getId, WalletJpaEntity::getOwnerUserId, (a, b) -> a));
    }

    private static BigDecimal computeBalanceBefore(TransactionType type, BigDecimal balanceAfter, BigDecimal amount) {
        return switch (type) {
            case DEPOSIT, ORDER_CREDIT, WITHDRAWAL_REFUND, PROFIT_SHARE -> balanceAfter.subtract(amount);
            case WITHDRAWAL, ORDER_DEBIT, ORDER_REFUND, PLATFORM_FEE -> balanceAfter.add(amount);
        };
    }

    private static WalletTransaction toDomain(WalletTransactionJpaEntity e, Long userId) {
        Long orderRef = null;
        if (REF_ORDER.equals(e.getReferenceType()) && e.getReferenceId() != null) {
            try {
                orderRef = Long.parseLong(e.getReferenceId());
            } catch (NumberFormatException ignored) {
                orderRef = null;
            }
        }
        return new WalletTransaction(e.getId(), e.getWalletId(), userId,
                TransactionType.valueOf(e.getTxType()), e.getAmountUsd(), e.getBalanceAfter(),
                orderRef, e.getIdempotencyKey(), e.getDescription(), e.getCreatedAt());
    }
}
