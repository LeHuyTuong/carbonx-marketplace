package com.carbonx.trading.infrastructure.persistence.repository;

import com.carbonx.trading.infrastructure.persistence.entity.OrderSagaInstanceJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderSagaInstanceJpaRepository extends JpaRepository<OrderSagaInstanceJpaEntity, String> {

    Optional<OrderSagaInstanceJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<OrderSagaInstanceJpaEntity> findBySagaStateInAndTimeoutAtLessThanEqualOrderByTimeoutAtAsc(
            Collection<String> states, Instant cutoff, Pageable pageable);
}
