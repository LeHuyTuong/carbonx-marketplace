package com.carbonx.ledger.domain.wallet;

import com.carbonx.ledger.domain.shared.LedgerDomainErrorCode;
import com.carbonx.ledger.domain.shared.LedgerDomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record Withdrawal(
        Long id,
        Long userId,
        BigDecimal amount,
        WithdrawalStatus status,
        String bankName,
        String accountNumber,
        String accountHolder,
        Long processedBy,
        Instant processedAt,
        Instant createdAt
) {
    public Withdrawal {
        if (userId == null) {
            throw LedgerDomainException.required("userId");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new LedgerDomainException(LedgerDomainErrorCode.AMOUNT_NOT_POSITIVE);
        }
        amount = amount.setScale(4, RoundingMode.HALF_UP);
        status = status == null ? WithdrawalStatus.PENDING : status;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static Withdrawal request(Long userId, BigDecimal amount, PaymentDetails bank) {
        return new Withdrawal(null, userId, amount, WithdrawalStatus.PENDING,
                bank != null ? bank.bankName() : null,
                bank != null ? bank.accountNumber() : null,
                bank != null ? bank.accountHolder() : null,
                null, null, Instant.now());
    }

    public Withdrawal decide(Long adminId, boolean approved) {
        if (status != WithdrawalStatus.PENDING) {
            throw new LedgerDomainException(LedgerDomainErrorCode.INVALID_WITHDRAWAL_STATE, id, status);
        }
        return new Withdrawal(id, userId, amount, approved ? WithdrawalStatus.APPROVED : WithdrawalStatus.REJECTED,
                bankName, accountNumber, accountHolder, adminId, Instant.now(), createdAt);
    }
}
