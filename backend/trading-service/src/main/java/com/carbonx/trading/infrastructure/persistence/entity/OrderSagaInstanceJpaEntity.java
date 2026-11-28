package com.carbonx.trading.infrastructure.persistence.entity;

import com.carbonx.trading.domain.saga.OrderSagaState;
import com.carbonx.trading.domain.saga.OrderSagaStep;
import com.carbonx.trading.domain.saga.SagaInstance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** JPA row of {@code order_saga_instances}: the persistent six-state saga state machine. */
@Entity
@Table(name = "order_saga_instances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderSagaInstanceJpaEntity {

    @Id
    @Column(name = "saga_id", nullable = false, length = 64)
    private String sagaId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "saga_state", nullable = false, length = 32)
    private String sagaState;

    @Column(name = "current_step", nullable = false, length = 64)
    private String currentStep;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "timeout_at", nullable = false)
    private Instant timeoutAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public static OrderSagaInstanceJpaEntity empty() {
        return new OrderSagaInstanceJpaEntity();
    }

    public void apply(SagaInstance saga, String traceId) {
        this.sagaId = saga.sagaId();
        this.idempotencyKey = saga.idempotencyKey();
        this.orderId = saga.orderId();
        this.listingId = saga.listingId();
        this.sagaState = saga.state().name();
        this.currentStep = saga.currentStep().name();
        this.retryCount = saga.retryCount();
        this.timeoutAt = saga.timeoutAt();
        this.lastError = saga.lastError();
        this.traceId = traceId;
    }

    public SagaInstance toDomain() {
        return new SagaInstance(sagaId, idempotencyKey, orderId, listingId, OrderSagaState.valueOf(sagaState),
                OrderSagaStep.valueOf(currentStep), retryCount, timeoutAt, lastError);
    }
}
