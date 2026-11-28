package com.carbonx.trading.application.port.out;

/** Reverses a settled wallet movement when the saga fails after payment. */
public interface WalletRefundPort {

    void refundOrderPayment(String idempotencyKey, Long orderRef, String reason);
}
