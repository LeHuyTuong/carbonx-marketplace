package com.carbonx.trading.infrastructure.persistence.adapter;

import com.carbonx.trading.application.exception.TradingApplicationException;
import com.carbonx.trading.application.port.out.OrderSagaOrchestratorPort;
import com.carbonx.trading.domain.saga.OrderSagaState;
import com.carbonx.trading.domain.saga.OrderSagaStep;
import com.carbonx.trading.domain.saga.SagaInstance;
import com.carbonx.trading.infrastructure.persistence.entity.OrderSagaInstanceJpaEntity;
import com.carbonx.trading.infrastructure.persistence.entity.OrderSagaTransitionLogJpaEntity;
import com.carbonx.trading.infrastructure.persistence.repository.OrderSagaInstanceJpaRepository;
import com.carbonx.trading.infrastructure.persistence.repository.OrderSagaTransitionLogJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persists the saga state machine in {@code order_saga_instances} and appends every hop to
 * {@code order_saga_transition_logs}. Same-state transitions are step-cursor advances; a transition
 * to the state the saga already has is an idempotent replay and is ignored.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestratorAdapter implements OrderSagaOrchestratorPort {

    private final OrderSagaInstanceJpaRepository instanceRepository;
    private final OrderSagaTransitionLogJpaRepository transitionLogRepository;

    @Override
    @Transactional
    public String beginSaga(String idempotencyKey, Long orderId, Long listingId, Instant timeoutAt) {
        SagaInstance saga = SagaInstance.begin(idempotencyKey, orderId, listingId, timeoutAt);
        OrderSagaInstanceJpaEntity entity = OrderSagaInstanceJpaEntity.empty();
        entity.apply(saga, MDC.get("traceId"));
        instanceRepository.save(entity);
        return saga.sagaId();
    }

    @Override
    @Transactional
    public void transitionState(String sagaId, OrderSagaState expectedCurrent, OrderSagaState target,
                                OrderSagaStep step, String detail) {
        OrderSagaInstanceJpaEntity entity = instanceRepository.findById(sagaId)
                .orElseThrow(() -> TradingApplicationException.notFound("Order Saga not found: " + sagaId));
        SagaInstance current = entity.toDomain();

        if (current.state() == target) {
            log.debug("Saga {} is already {}, ignoring transition to {} at step {}", sagaId, target, target, step);
            if (current.currentStep() == step) {
                return;
            }
            entity.apply(current.transitionTo(target, step, detail), MDC.get("traceId"));
            instanceRepository.save(entity);
            return;
        }
        if (current.state() != expectedCurrent) {
            throw TradingApplicationException.conflict("Order Saga " + sagaId + " is " + current.state()
                    + " but the step expected " + expectedCurrent);
        }

        SagaInstance next = current.transitionTo(target, step, detail);
        entity.apply(next, MDC.get("traceId"));
        instanceRepository.save(entity);
        transitionLogRepository.save(OrderSagaTransitionLogJpaEntity.of(sagaId, expectedCurrent, target, step,
                detail));
        log.info("Saga {} {} -> {} at step {}", sagaId, expectedCurrent, target, step);
    }

    @Override
    @Transactional
    public void recordRetryAttempt(String sagaId, OrderSagaStep failedStep, String errorMessage) {
        OrderSagaInstanceJpaEntity entity = instanceRepository.findById(sagaId)
                .orElseThrow(() -> TradingApplicationException.notFound("Order Saga not found: " + sagaId));
        SagaInstance retried = entity.toDomain().retried(failedStep, errorMessage);
        entity.apply(retried, MDC.get("traceId"));
        instanceRepository.save(entity);
        log.warn("Saga {} recorded retry #{} at step {}: {}", sagaId, retried.retryCount(), failedStep, errorMessage);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SagaInstanceSnapshot> findByIdempotencyKey(String idempotencyKey) {
        return instanceRepository.findByIdempotencyKey(idempotencyKey).map(SagaOrchestratorAdapter::snapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SagaInstanceSnapshot> findTimedOutOrRecoverableSagas(Instant cutoffTime, int batchSize) {
        int size = batchSize <= 0 ? 50 : batchSize;
        return instanceRepository
                .findBySagaStateInAndTimeoutAtLessThanEqualOrderByTimeoutAtAsc(
                        List.of(OrderSagaState.PENDING.name(), OrderSagaState.EXECUTING.name()),
                        cutoffTime, PageRequest.of(0, size))
                .stream()
                .map(SagaOrchestratorAdapter::snapshot)
                .toList();
    }

    private static SagaInstanceSnapshot snapshot(OrderSagaInstanceJpaEntity entity) {
        SagaInstance saga = entity.toDomain();
        return new SagaInstanceSnapshot(saga.sagaId(), saga.idempotencyKey(), saga.orderId(), saga.listingId(),
                saga.state(), saga.currentStep(), saga.retryCount(), saga.timeoutAt(), saga.lastError());
    }
}
