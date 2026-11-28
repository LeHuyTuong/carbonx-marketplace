package com.carbonx.trading.infrastructure.persistence.repository;

import com.carbonx.trading.infrastructure.persistence.entity.OrderSagaTransitionLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderSagaTransitionLogJpaRepository
        extends JpaRepository<OrderSagaTransitionLogJpaEntity, Long> {

    List<OrderSagaTransitionLogJpaEntity> findBySagaIdOrderByIdAsc(String sagaId);
}
