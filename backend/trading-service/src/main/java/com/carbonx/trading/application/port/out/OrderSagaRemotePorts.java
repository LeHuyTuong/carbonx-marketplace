package com.carbonx.trading.application.port.out;

import com.carbonx.common.domain.Money;

/**
 * Forward Distributed Saga Command Ports (implemented via @FeignClient with
 * spring.cloud.openfeign.circuitbreaker.enabled=true + Resilience4j fallback).
 * Backward rollbacks live exclusively in {@link OrderCompensationPort}.
 */
public interface OrderSagaRemotePorts {

    void debitBuyerAndCreditSeller(String idempotencyKey, Long orderRef, Long buyerUserId, Long sellerUserId, Money gross, Money fee);

    void transferCreditsToBuyer(String idempotencyKey, Long sellerHoldingId, Long buyerCompanyId, long quantity, String orderCode);
}
