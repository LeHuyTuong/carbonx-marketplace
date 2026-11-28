package com.carbonx.trading.application.port.out;

import com.carbonx.common.domain.Money;

/**
 * Distributed Saga Outbound Ports (implemented via @FeignClient + Resilience4j CircuitBreaker & Retry).
 * Includes explicit Compensating Transactions if any downstream step fails!
 */
public interface OrderSagaRemotePorts {
    void debitBuyerAndCreditSeller(String idempotencyKey, Long orderRef, Long buyerUserId, Long sellerUserId, Money gross, Money fee);
    void compensateRefundWalletSettlement(String idempotencyKey, Long orderRef, String reason);

    void transferCreditsToBuyer(String idempotencyKey, Long sellerHoldingId, Long buyerCompanyId, long quantity, String orderCode);
}
