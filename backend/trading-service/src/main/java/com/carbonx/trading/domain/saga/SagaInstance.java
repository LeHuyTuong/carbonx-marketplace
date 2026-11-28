package com.carbonx.trading.domain.saga;

import com.carbonx.trading.domain.shared.DomainValidationException;

import java.time.Instant;
import java.util.UUID;

/**
 * In-memory view of one row of {@code order_saga_instances}. The record owns the state-machine
 * invariants: a transition is only allowed when {@link OrderSagaState#canTransitionTo} accepts it,
 * and a terminal saga can never move again.
 */
public record SagaInstance(
        String sagaId,
        String idempotencyKey,
        Long orderId,
        Long listingId,
        OrderSagaState state,
        OrderSagaStep currentStep,
        int retryCount,
        Instant timeoutAt,
        String lastError
) {

    public SagaInstance {
        if (sagaId == null || sagaId.isBlank()) {
            throw new DomainValidationException("sagaId is required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new DomainValidationException("idempotencyKey is required");
        }
        if (listingId == null) {
            throw new DomainValidationException("listingId is required");
        }
        if (state == null || currentStep == null) {
            throw new DomainValidationException("state and currentStep are required");
        }
        if (timeoutAt == null) {
            throw new DomainValidationException("timeoutAt is required");
        }
    }

    public static SagaInstance begin(String idempotencyKey, Long orderId, Long listingId, Instant timeoutAt) {
        return new SagaInstance(UUID.randomUUID().toString(), idempotencyKey, orderId, listingId,
                OrderSagaState.PENDING, OrderSagaStep.RESERVE_LISTING, 0, timeoutAt, null);
    }

    /** PENDING -> EXECUTING -> ... ; moving to the current state only advances the step cursor. */
    public SagaInstance transitionTo(OrderSagaState target, OrderSagaStep step, String detail) {
        if (state == target) {
            return new SagaInstance(sagaId, idempotencyKey, orderId, listingId, state, step, retryCount,
                    timeoutAt, lastError);
        }
        if (!state.canTransitionTo(target)) {
            throw new DomainValidationException("Illegal saga transition " + state + " -> " + target
                    + " (" + detail + ")");
        }
        return new SagaInstance(sagaId, idempotencyKey, orderId, listingId, target, step, retryCount,
                timeoutAt, target == OrderSagaState.FAILED ? detail : lastError);
    }

    public SagaInstance retried(OrderSagaStep failedStep, String error) {
        return new SagaInstance(sagaId, idempotencyKey, orderId, listingId, state, failedStep, retryCount + 1,
                timeoutAt, error);
    }

    public SagaInstance withOrderId(Long newOrderId) {
        return new SagaInstance(sagaId, idempotencyKey, newOrderId, listingId, state, currentStep, retryCount,
                timeoutAt, lastError);
    }
}
