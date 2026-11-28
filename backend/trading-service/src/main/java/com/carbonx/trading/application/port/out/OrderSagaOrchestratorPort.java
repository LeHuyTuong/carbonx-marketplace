package com.carbonx.trading.application.port.out;

import com.carbonx.trading.domain.saga.OrderSagaState;
import com.carbonx.trading.domain.saga.OrderSagaStep;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Central Saga State-Machine Port (Saga + Transactional Outbox).
 * Architectural Boundary: Tracks only workflow states, step cursors, timeouts & retries;
 * MUST NOT embed domain pricing/tax/inventory formulas (prevents Distributed Monolith bloat).
 */
public interface OrderSagaOrchestratorPort {

    String beginSaga(String idempotencyKey, Long orderId, Long listingId, Instant timeoutAt);

    void transitionState(String sagaId, OrderSagaState expectedCurrent, OrderSagaState target, OrderSagaStep step, String detail);

    void recordRetryAttempt(String sagaId, OrderSagaStep failedStep, String errorMessage);

    Optional<SagaInstanceSnapshot> findByIdempotencyKey(String idempotencyKey);

    List<SagaInstanceSnapshot> findTimedOutOrRecoverableSagas(Instant cutoffTime, int batchSize);

    record SagaInstanceSnapshot(
            String sagaId,
            String idempotencyKey,
            Long orderId,
            Long listingId,
            OrderSagaState state,
            OrderSagaStep currentStep,
            int retryCount,
            Instant timeoutAt,
            String lastError
    ) {}
}
