package com.carbonx.ledger.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public final class LedgerDtos {
    private LedgerDtos() {}

    public record SettlePaymentRequest(
            @NotBlank String idempotencyKey,
            @NotNull Long buyerUserId,
            Long sellerUserId,
            @NotNull @DecimalMin("0.0001") BigDecimal grossAmount,
            BigDecimal platformFee) {}

    public record RefundPaymentRequest(
            @NotBlank String idempotencyKey,
            String reason) {}

    public record DepositRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String provider) {}

    public record PaymentDetailsInput(
            String bankName,
            String accountNumber,
            String accountHolder,
            String branch,
            PaymentDetailsInput data) {
        public PaymentDetailsInput resolved() {
            return data != null ? data : this;
        }
    }

    public record ProfitShareRequest(
            @NotNull Long sourceReportId,
            String idempotencyKey) {}

    public record IdResponse(Long id) {}
}
