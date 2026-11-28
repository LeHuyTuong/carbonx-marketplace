package com.carbonx.trading.application.service;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.application.exception.TradingApplicationException;
import com.carbonx.trading.application.exception.TradingErrorCode;
import com.carbonx.trading.application.port.out.OrderCompensationPort;
import com.carbonx.trading.application.port.out.OrderSagaOrchestratorPort;
import com.carbonx.trading.application.port.out.OrderSagaOrchestratorPort.SagaInstanceSnapshot;
import com.carbonx.trading.application.port.out.OrderSagaRemotePorts;
import com.carbonx.trading.domain.saga.OrderSagaState;
import com.carbonx.trading.domain.saga.OrderSagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSagaServiceTest {

    @Mock
    private OrderSagaOrchestratorPort orchestrator;
    @Mock
    private OrderSagaTransactionSteps steps;
    @Mock
    private OrderSagaRemotePorts remotePorts;
    @Mock
    private OrderCompensationPort compensation;

    private OrderSagaService sagaService;

    @BeforeEach
    void setUp() {
        sagaService = new OrderSagaService(orchestrator, steps, remotePorts, compensation);
    }

    @Test
    void happyPathCreditsRealSellerUserIdAndTransfersAlreadyLockedHolding() {
        SagaReservationContext ctx = new SagaReservationContext(
                "saga-1", 500L, "ORD-20261128-AAAA1111", 700L, 55L, 10L, 42L, 7L, 10L,
                Money.of(new BigDecimal("150.00")), Money.of(new BigDecimal("7.50")));
        when(orchestrator.findByIdempotencyKey("buy-key-1")).thenReturn(Optional.empty());
        when(steps.reserveListingAndStartSaga("buy-key-1", 99L, 55L, 10L)).thenReturn(ctx);

        Long orderId = sagaService.executeInstantBuy("buy-key-1", 99L, 88L, 55L, 10L);

        assertThat(orderId).isEqualTo(500L);
        InOrder ordered = inOrder(orchestrator, remotePorts, steps);
        ordered.verify(remotePorts).debitBuyerAndCreditSeller(
                "buy-key-1", 500L, 88L, 7L, Money.of(new BigDecimal("150.00")), Money.of(new BigDecimal("7.50")));
        ordered.verify(remotePorts).transferCreditsToBuyer(
                "buy-key-1", 10L, 99L, 10L, "ORD-20261128-AAAA1111");
        ordered.verify(steps).finalizeOrder("saga-1", 500L, 700L);
    }

    @Test
    void transferFailureCompensatesPaymentAndListingWithoutUnlockingActiveListingHolding() {
        SagaReservationContext ctx = new SagaReservationContext(
                "saga-2", 501L, "ORD-20261128-BBBB2222", 701L, 55L, 10L, 42L, 7L, 10L,
                Money.of(new BigDecimal("150.00")), Money.of(new BigDecimal("7.50")));
        when(orchestrator.findByIdempotencyKey("buy-key-2")).thenReturn(Optional.empty());
        when(steps.reserveListingAndStartSaga("buy-key-2", 99L, 55L, 10L)).thenReturn(ctx);
        doThrow(TradingApplicationException.conflict("Insufficient locked balance"))
                .when(remotePorts).transferCreditsToBuyer("buy-key-2", 10L, 99L, 10L, "ORD-20261128-BBBB2222");

        assertThatThrownBy(() -> sagaService.executeInstantBuy("buy-key-2", 99L, 88L, 55L, 10L))
                .isInstanceOf(TradingApplicationException.class)
                .extracting(ex -> ((TradingApplicationException) ex).code())
                .isEqualTo(TradingErrorCode.SAGA_COMPENSATED);

        verify(compensation).compensateRefundWalletSettlement(eq("buy-key-2:refund"), eq(501L), anyString());
        verify(compensation).compensateCancelOrderAndReleaseListing(
                eq("buy-key-2"), eq(501L), eq(55L), eq(10L), anyString());
        verify(compensation, never()).compensateReleaseLockedCredits(any(), any(), eq(10L), any());
        verify(steps).markCompensated(eq("saga-2"), eq(501L), eq(55L), eq(OrderSagaStep.TRANSFER_CREDITS), anyString());
    }

    @Test
    void recoverTimedOutSagasCompensatesTransferTimeoutAndFinalizesFinalizeTimeout() {
        SagaInstanceSnapshot transferTimedOut = new SagaInstanceSnapshot(
                "saga-stuck-1", "k-1", 601L, 55L, OrderSagaState.EXECUTING,
                OrderSagaStep.TRANSFER_CREDITS, 0, Instant.now().minusSeconds(60), null);
        SagaInstanceSnapshot finalizeTimedOut = new SagaInstanceSnapshot(
                "saga-stuck-2", "k-2", 602L, 56L, OrderSagaState.EXECUTING,
                OrderSagaStep.FINALIZE_ORDER, 1, Instant.now().minusSeconds(30), "db glitch");

        when(orchestrator.findTimedOutOrRecoverableSagas(any(), eq(50)))
                .thenReturn(List.of(transferTimedOut, finalizeTimedOut));
        when(steps.findOrderQuantity(601L)).thenReturn(15L);

        int recovered = sagaService.recoverTimedOutSagas(50);

        assertThat(recovered).isEqualTo(2);
        verify(compensation).compensateRefundWalletSettlement(eq("k-1:refund"), eq(601L), anyString());
        verify(compensation).compensateCancelOrderAndReleaseListing(eq("k-1"), eq(601L), eq(55L), eq(15L), anyString());
        verify(steps).markCompensated(eq("saga-stuck-1"), eq(601L), eq(55L), eq(OrderSagaStep.TRANSFER_CREDITS), anyString());
        verify(steps).finalizeOrderByKey("saga-stuck-2", 602L, "k-2");
    }
}
