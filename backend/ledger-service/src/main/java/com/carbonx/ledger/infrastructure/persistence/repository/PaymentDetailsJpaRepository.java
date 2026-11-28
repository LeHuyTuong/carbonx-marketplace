package com.carbonx.ledger.infrastructure.persistence.repository;

import com.carbonx.ledger.infrastructure.persistence.entity.PaymentDetailsJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentDetailsJpaRepository extends JpaRepository<PaymentDetailsJpaEntity, Long> {
    Optional<PaymentDetailsJpaEntity> findByUserId(Long userId);
}
