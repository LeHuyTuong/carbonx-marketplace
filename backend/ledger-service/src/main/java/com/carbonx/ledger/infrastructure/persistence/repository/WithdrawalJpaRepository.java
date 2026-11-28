package com.carbonx.ledger.infrastructure.persistence.repository;

import com.carbonx.ledger.infrastructure.persistence.entity.WithdrawalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WithdrawalJpaRepository extends JpaRepository<WithdrawalJpaEntity, Long> {
    List<WithdrawalJpaEntity> findByWalletIdOrderByIdDesc(Long walletId);
    List<WithdrawalJpaEntity> findAllByOrderByIdDesc();
}
