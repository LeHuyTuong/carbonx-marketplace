package com.carbonx.trading.application.service;

import com.carbonx.trading.application.port.out.OrderCompensationPort;
import com.carbonx.trading.application.port.out.OrderCreditLockPort;
import com.carbonx.trading.application.port.out.WalletRefundPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Backward half of the saga. Each method is Idempotent (keyed by the saga's idempotency key),
 * Reversible (wallet refund, holding release, listing quantity back) and Reliable (the remote port
 * is retried by Resilience4j and a hard failure escalates to a 503 so it is never dropped).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCompensationService implements OrderCompensationPort {

    private final OrderSagaTransactionSteps steps;
    private final OrderCreditLockPort creditLock;
    private final WalletRefundPort walletRefund;

    @Override
    public void compensateCancelOrderAndReleaseListing(String idempotencyKey, Long orderId, Long listingId,
                                                       long quantity, String reason) {
        steps.cancelOrderAndReleaseListing(idempotencyKey, orderId, listingId, quantity, reason);
    }

    @Override
    public void compensateRefundWalletSettlement(String idempotencyKey, Long orderId, String reason) {
        log.info("Refunding wallet settlement of order {} key={} ({})", orderId, idempotencyKey, reason);
        walletRefund.refundOrderPayment(idempotencyKey, orderId, reason);
    }

    @Override
    public void compensateReleaseLockedCredits(String idempotencyKey, Long holdingId, long quantity, String reason) {
        log.info("Releasing {} locked credits of holding {} key={} ({})", quantity, holdingId, idempotencyKey, reason);
        creditLock.releaseLockedCredits(idempotencyKey, holdingId, quantity, reason);
    }
}
