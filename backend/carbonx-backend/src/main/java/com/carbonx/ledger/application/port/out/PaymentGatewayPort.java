package com.carbonx.ledger.application.port.out;

import com.carbonx.shared.domain.Money;

/** Outbound Port: External Payment Gateway integration (VNPay / Stripe / PayPal). */
public interface PaymentGatewayPort {
    String createCheckoutUrl(String provider, String orderRef, Money amount, String returnUrl);

    boolean verifyCallbackSignature(String provider, String rawPayload, String signatureHeader);
}
