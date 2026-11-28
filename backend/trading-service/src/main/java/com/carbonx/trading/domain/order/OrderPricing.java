package com.carbonx.trading.domain.order;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.domain.shared.DomainValidationException;

import java.math.BigDecimal;

/**
 * Price arithmetic of one trade order. Lives in the domain (not in the saga orchestrator) so the
 * orchestrator only tracks states, steps, timeouts and retries.
 */
public record OrderPricing(Money unitPrice, long quantity, BigDecimal feeRate) {

    /** Default CarbonX platform commission on the gross trade value (overridden by Admin settings). */
    public static final BigDecimal DEFAULT_PLATFORM_FEE_RATE = new BigDecimal("0.05");
    public static final BigDecimal MAX_PLATFORM_FEE_RATE = new BigDecimal("0.50");

    public OrderPricing {
        if (unitPrice == null || unitPrice.isNegative()) {
            throw new DomainValidationException("unitPrice must be zero or positive");
        }
        if (quantity <= 0) {
            throw new DomainValidationException("quantity must be positive");
        }
        if (feeRate == null || feeRate.signum() < 0 || feeRate.compareTo(MAX_PLATFORM_FEE_RATE) > 0) {
            throw new DomainValidationException("feeRate must be between 0 and 0.50");
        }
    }

    public static OrderPricing of(Money unitPrice, long quantity) {
        return of(unitPrice, quantity, DEFAULT_PLATFORM_FEE_RATE);
    }

    public static OrderPricing of(Money unitPrice, long quantity, BigDecimal feeRate) {
        return new OrderPricing(unitPrice, quantity, feeRate == null ? DEFAULT_PLATFORM_FEE_RATE : feeRate);
    }

    public Money grossAmount() {
        return unitPrice.multiply(quantity);
    }

    public Money platformFee() {
        return grossAmount().percentage(feeRate);
    }

    public Money netSellerAmount() {
        return grossAmount().subtract(platformFee());
    }
}
