package com.carbonx.ledger.domain.wallet;

import com.carbonx.ledger.domain.shared.LedgerDomainErrorCode;
import com.carbonx.ledger.domain.shared.LedgerDomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Immutable wallet aggregate: balance is always >= 0 and scaled to 4 decimal places. */
public record Wallet(
        Long id,
        Long userId,
        BigDecimal balance,
        String currency,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static final String DEFAULT_CURRENCY = "USD";

    public Wallet {
        if (userId == null) {
            throw LedgerDomainException.required("userId");
        }
        balance = (balance == null ? BigDecimal.ZERO : balance).setScale(4, RoundingMode.HALF_UP);
        if (balance.signum() < 0) {
            throw new LedgerDomainException(LedgerDomainErrorCode.NEGATIVE_BALANCE);
        }
        currency = currency == null || currency.isBlank() ? DEFAULT_CURRENCY : currency.trim().toUpperCase();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public static Wallet createForUser(Long userId) {
        return new Wallet(null, userId, BigDecimal.ZERO, DEFAULT_CURRENCY, 0L, Instant.now(), Instant.now());
    }

    public Wallet credit(BigDecimal amount) {
        BigDecimal positive = requirePositive(amount);
        return new Wallet(id, userId, balance.add(positive), currency, version, createdAt, Instant.now());
    }

    public Wallet debit(BigDecimal amount) {
        BigDecimal positive = requirePositive(amount);
        if (balance.compareTo(positive) < 0) {
            throw new LedgerDomainException(LedgerDomainErrorCode.INSUFFICIENT_FUNDS);
        }
        return new Wallet(id, userId, balance.subtract(positive), currency, version, createdAt, Instant.now());
    }

    private static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new LedgerDomainException(LedgerDomainErrorCode.AMOUNT_NOT_POSITIVE);
        }
        return amount.setScale(4, RoundingMode.HALF_UP);
    }
}
