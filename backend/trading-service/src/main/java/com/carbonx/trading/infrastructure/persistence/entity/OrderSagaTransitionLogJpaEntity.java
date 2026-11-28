package com.carbonx.trading.infrastructure.persistence.entity;

import com.carbonx.trading.domain.saga.OrderSagaState;
import com.carbonx.trading.domain.saga.OrderSagaStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Append-only audit trail of every saga transition ({@code order_saga_transition_logs}). */
@Entity
@Table(name = "order_saga_transition_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderSagaTransitionLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", nullable = false, length = 64)
    private String sagaId;

    @Column(name = "from_state", nullable = false, length = 32)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 32)
    private String toState;

    @Column(name = "step_name", nullable = false, length = 64)
    private String stepName;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "occurred_at", insertable = false, updatable = false)
    private Instant occurredAt;

    public static OrderSagaTransitionLogJpaEntity of(String sagaId, OrderSagaState from, OrderSagaState to,
                                                     OrderSagaStep step, String detail) {
        OrderSagaTransitionLogJpaEntity entity = new OrderSagaTransitionLogJpaEntity();
        entity.sagaId = sagaId;
        entity.fromState = from.name();
        entity.toState = to.name();
        entity.stepName = step.name();
        entity.detail = detail == null ? null : detail.length() <= 2000 ? detail : detail.substring(0, 2000);
        return entity;
    }
}
