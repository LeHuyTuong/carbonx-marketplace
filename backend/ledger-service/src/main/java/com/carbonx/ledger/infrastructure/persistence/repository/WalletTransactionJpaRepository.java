package com.carbonx.ledger.infrastructure.persistence.repository;

import com.carbonx.ledger.infrastructure.persistence.entity.WalletTransactionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WalletTransactionJpaRepository extends JpaRepository<WalletTransactionJpaEntity, Long> {

    Optional<WalletTransactionJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<WalletTransactionJpaEntity> findByReferenceTypeAndReferenceIdOrderByIdAsc(
            String referenceType, String referenceId);

    List<WalletTransactionJpaEntity> findByWalletIdOrderByIdDesc(Long walletId);

    List<WalletTransactionJpaEntity> findAllByOrderByIdDesc();
}
