package com.carbonx.trading.application.service;

import com.carbonx.trading.application.exception.TradingApplicationException;
import com.carbonx.trading.application.exception.TradingErrorCode;
import com.carbonx.trading.domain.shared.DomainValidationException;
import com.carbonx.trading.domain.shared.ListingUnavailableException;
import com.carbonx.trading.application.port.out.OrderCompensationPort;
import com.carbonx.trading.application.port.out.OrderSagaOrchestratorPort;
import com.carbonx.trading.application.port.out.OrderSagaOrchestratorPort.SagaInstanceSnapshot;
import com.carbonx.trading.application.port.out.OrderSagaRemotePorts;
import com.carbonx.trading.domain.saga.OrderSagaState;
import com.carbonx.trading.domain.saga.OrderSagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The Order Saga engine: RESERVE_LISTING -> SETTLE_PAYMENT -> TRANSFER_CREDITS -> FINALIZE_ORDER,
 * with a reverse compensation chain when any of the first three steps fails. Holding credits are
 * already locked when the listing is created, so a failed purchase restores listing availability
 * while leaving seller credits locked for subsequent buyers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaService {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final OrderSagaOrchestratorPort orchestrator;
    private final OrderSagaTransactionSteps steps;
    private final OrderSagaRemotePorts remotePorts;
    private final OrderCompensationPort compensation;

    /** Entry point of {@code POST /api/v1/trading/orders}; returns the finished order id. */
    public Long executeInstantBuy(String idempotencyKey, Long buyerCompanyId, Long buyerUserId, Long listingId,
                                  long quantity) {
        String key = requireKey(idempotencyKey);
        requireId(buyerCompanyId, "buyerCompanyId");
        requireId(listingId, "listingId");
        if (quantity <= 0) {
            throw TradingApplicationException.badRequest("quantity must be positive");
        }

        Optional<SagaInstanceSnapshot> previous = orchestrator.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            return replay(previous.get());
        }

        SagaReservationContext context;
        try {
            context = steps.reserveListingAndStartSaga(key, buyerCompanyId, listingId, quantity);
        } catch (ListingUnavailableException | DomainValidationException ex) {
            throw TradingApplicationException.listingUnavailable(ex.getMessage());
        }

        boolean paymentSettled = false;
        OrderSagaStep currentStep = OrderSagaStep.RESERVE_LISTING;
        try {
            currentStep = OrderSagaStep.SETTLE_PAYMENT;
            advance(context.sagaId(), currentStep, "settling payment");
            remotePorts.debitBuyerAndCreditSeller(key, context.orderId(), buyerUserId,
                    context.effectiveSellerUserId(), context.grossAmount(), context.platformFee());
            paymentSettled = true;

            currentStep = OrderSagaStep.TRANSFER_CREDITS;
            advance(context.sagaId(), currentStep, "payment settled, transferring credits");
            remotePorts.transferCreditsToBuyer(key, context.holdingId(), buyerCompanyId, quantity,
                    context.orderCode());

            currentStep = OrderSagaStep.FINALIZE_ORDER;
            advance(context.sagaId(), currentStep, "credits transferred, finalizing order");
            steps.finalizeOrder(context.sagaId(), context.orderId(), context.reservationId());
            return context.orderId();
        } catch (RuntimeException failure) {
            // Money and credits already moved: the local finalize is retryable with the same key,
            // never compensated, or the buyer would lose the credits it just received.
            if (currentStep == OrderSagaStep.FINALIZE_ORDER && paymentSettled) {
                orchestrator.recordRetryAttempt(context.sagaId(), currentStep, describe(failure));
                log.error("Order {} moved money and credits but could not be finalized: {}",
                        context.orderCode(), failure.getMessage());
                throw TradingApplicationException.unavailable(
                        "Finalization of order " + context.orderCode() + " (retry the same idempotency key)",
                        failure);
            }
            throw compensate(context, key, currentStep, failure, paymentSettled);
        }
    }

    /** Sweeps timed-out PENDING/EXECUTING sagas so crashes never strand reservations. */
    public int recoverTimedOutSagas(int batchSize) {
        List<SagaInstanceSnapshot> timedOut = orchestrator.findTimedOutOrRecoverableSagas(Instant.now(), batchSize);
        int recovered = 0;
        for (SagaInstanceSnapshot saga : timedOut) {
            try {
                recoverTimedOutSaga(saga);
                recovered++;
            } catch (RuntimeException ex) {
                orchestrator.recordRetryAttempt(saga.sagaId(), saga.currentStep(), describe(ex));
                log.warn("Timeout recovery failed for saga {} at {}: {}",
                        saga.sagaId(), saga.currentStep(), ex.getMessage());
            }
        }
        return recovered;
    }

    private void recoverTimedOutSaga(SagaInstanceSnapshot saga) {
        if (saga.currentStep() == OrderSagaStep.FINALIZE_ORDER) {
            steps.finalizeOrderByKey(saga.sagaId(), saga.orderId(), saga.idempotencyKey());
            return;
        }
        String reason = "Saga timed out at step " + saga.currentStep();
        if (saga.state() == OrderSagaState.PENDING) {
            orchestrator.transitionState(saga.sagaId(), OrderSagaState.PENDING, OrderSagaState.EXECUTING,
                    saga.currentStep(), "moving timed-out PENDING saga into compensation");
        }
        orchestrator.transitionState(saga.sagaId(), OrderSagaState.EXECUTING, OrderSagaState.FAILED,
                saga.currentStep(), reason);
        orchestrator.transitionState(saga.sagaId(), OrderSagaState.FAILED, OrderSagaState.COMPENSATING,
                saga.currentStep(), "timed-out saga recovery compensation");

        if (saga.currentStep() == OrderSagaStep.TRANSFER_CREDITS && saga.orderId() != null) {
            compensation.compensateRefundWalletSettlement(saga.idempotencyKey() + ":refund", saga.orderId(),
                    reason);
        } else if (saga.currentStep() == OrderSagaStep.SETTLE_PAYMENT && saga.orderId() != null) {
            try {
                compensation.compensateRefundWalletSettlement(saga.idempotencyKey() + ":refund", saga.orderId(),
                        reason);
            } catch (TradingApplicationException ex) {
                if (ex.code() != TradingErrorCode.NOT_FOUND) {
                    throw ex;
                }
            }
        }

        if (saga.orderId() != null) {
            Long quantity = steps.findOrderQuantity(saga.orderId());
            if (quantity != null && quantity > 0) {
                compensation.compensateCancelOrderAndReleaseListing(saga.idempotencyKey(), saga.orderId(),
                        saga.listingId(), quantity, reason);
            }
        }
        steps.markCompensated(saga.sagaId(), saga.orderId(), saga.listingId(), saga.currentStep(), reason);
    }

    private void advance(String sagaId, OrderSagaStep step, String detail) {
        orchestrator.transitionState(sagaId, OrderSagaState.EXECUTING, OrderSagaState.EXECUTING, step, detail);
    }

    /**
     * FAILED -> COMPENSATING -> COMPENSATED in reverse step order, then surfaces a 409 so the buyer
     * knows the purchase did not happen. Compensation failures are recorded for retry and surface as
     * 503 instead of pretending the rollback succeeded.
     */
    private TradingApplicationException compensate(SagaReservationContext context, String key,
                                                    OrderSagaStep failedStep, RuntimeException failure,
                                                    boolean paymentSettled) {
        String reason = describe(failure);
        log.warn("Saga {} failed at {} ({}); compensating in reverse order", context.sagaId(), failedStep, reason);
        try {
            orchestrator.transitionState(context.sagaId(), OrderSagaState.EXECUTING, OrderSagaState.FAILED,
                    failedStep, reason);
            orchestrator.transitionState(context.sagaId(), OrderSagaState.FAILED, OrderSagaState.COMPENSATING,
                    failedStep, "refund -> restore listing -> cancel order");

            if (paymentSettled) {
                compensation.compensateRefundWalletSettlement(key + ":refund", context.orderId(), reason);
            }
            compensation.compensateCancelOrderAndReleaseListing(key, context.orderId(), context.listingId(),
                    context.quantity(), reason);
            steps.markCompensated(context.sagaId(), context.orderId(), context.listingId(), failedStep, reason);
        } catch (RuntimeException rollbackFailure) {
            orchestrator.recordRetryAttempt(context.sagaId(), failedStep, describe(rollbackFailure));
            log.error("Compensation of saga {} failed at {}: {}", context.sagaId(), failedStep,
                    rollbackFailure.getMessage());
            throw TradingApplicationException.unavailable(
                    "Rollback of order " + context.orderCode() + " (retry the same idempotency key)",
                    rollbackFailure);
        }
        return TradingApplicationException.sagaCompensated(context.orderCode(), failedStep.name(), reason);
    }

    /** A replayed request must never start a second saga, order or reservation. */
    private Long replay(SagaInstanceSnapshot snapshot) {
        return switch (snapshot.state()) {
            case COMPLETED -> {
                if (snapshot.orderId() == null) {
                    throw TradingApplicationException.unavailable("Order Saga " + snapshot.sagaId()
                            + " completed without an order id", new IllegalStateException("missing orderId"));
                }
                log.info("Idempotent replay of saga {}: order {} already settled", snapshot.sagaId(),
                        snapshot.orderId());
                yield snapshot.orderId();
            }
            case COMPENSATED, FAILED -> throw TradingApplicationException.sagaCompensated(
                    String.valueOf(snapshot.orderId()), snapshot.currentStep().name(),
                    snapshot.lastError() == null ? "the saga was already rolled back" : snapshot.lastError());
            case PENDING, EXECUTING, COMPENSATING -> throw TradingApplicationException.sagaInProgress(
                    snapshot.sagaId());
        };
    }

    private static String requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw TradingApplicationException.badRequest("Idempotency-Key header is required");
        }
        String key = idempotencyKey.trim();
        if (key.length() > 100) {
            throw TradingApplicationException.badRequest("Idempotency-Key must be at most 100 characters");
        }
        return key;
    }

    private static void requireId(Long value, String field) {
        if (value == null) {
            throw TradingApplicationException.badRequest(field + " is required");
        }
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
