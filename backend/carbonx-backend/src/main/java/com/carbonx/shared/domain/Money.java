package com.carbonx.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Immutable USD Money Value Object used across Financial & Trading Bounded Contexts. */
public record Money(BigDecimal amount) {
    public static final Money ZERO = new Money(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));

    public Money {
        Objects.requireNonNull(amount, "Money amount must not be null");
        amount = amount.setScale(4, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal value) {
        return new Money(value);
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    public Money multiply(long qty) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(qty)));
    }

    public Money percentage(BigDecimal rate) {
        return new Money(this.amount.multiply(rate));
    }

    public boolean isNegative() {
        return this.amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isLessThan(Money other) {
        return this.amount.compareTo(other.amount) < 0;
    }
}
