package com.carbonx.ledger.infrastructure.persistence.repository;

import com.carbonx.ledger.infrastructure.persistence.entity.WalletTransactionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WalletTransactionJpaRepository extends JpaRepository<WalletTransactionJpaEntity, Long> {

    Optional<WalletTransactionJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<WalletTransactionJpaEntity> findByOrderRefOrderByIdAsc(Long orderRef);

    List<WalletTransactionJpaEntity> findByUserIdOrderByIdDesc(Long userId);

    List<WalletTransactionJpaEntity> findAllByOrderByIdDesc();
}
