package com.carbonx.ledger.application.port.out;

import com.carbonx.ledger.domain.wallet.PaymentDetails;

import java.util.Optional;

public interface PaymentDetailsRepositoryPort {
    Optional<PaymentDetails> findByUserId(Long userId);
    PaymentDetails save(PaymentDetails details);
}
