package com.carbonx.trading.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

/**
 * Internal ledger channel used for the SETTLE_PAYMENT step and its wallet refund compensation.
 * The empty default URL lets Eureka/Spring Cloud LoadBalancer resolve the instance;
 * {@code carbonx.clients.ledger.base-url} pins a host for local runs.
 */
@FeignClient(name = "ledger-service", url = "${carbonx.clients.ledger.base-url:}",
        path = "/api/v1/internal/wallets")
public interface LedgerServiceClient {

    @PostMapping("/orders/{orderRef}/settle")
    void settleOrderPayment(@PathVariable("orderRef") Long orderRef, @RequestBody SettlePaymentRequest request);

    @PostMapping("/orders/{orderRef}/refund")
    void refundOrderPayment(@PathVariable("orderRef") Long orderRef, @RequestBody RefundPaymentRequest request);

    record SettlePaymentRequest(String idempotencyKey, Long buyerUserId, Long sellerUserId,
                                BigDecimal grossAmount, BigDecimal platformFee) {
    }

    record RefundPaymentRequest(String idempotencyKey, String reason) {
    }
}
