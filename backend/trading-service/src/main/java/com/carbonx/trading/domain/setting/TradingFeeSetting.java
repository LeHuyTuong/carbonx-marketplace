package com.carbonx.trading.domain.setting;

import com.carbonx.trading.domain.shared.DomainValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Immutable domain snapshot of the configurable marketplace transaction fee rate. */
public record TradingFeeSetting(
        BigDecimal feeRate,
        BigDecimal feePercent,
        Long updatedByUserId,
        Instant updatedAt
) {

    public static final BigDecimal DEFAULT_FEE_RATE = new BigDecimal("0.0500");
    public static final BigDecimal MAX_FEE_RATE = new BigDecimal("0.5000");

    public TradingFeeSetting {
        if (feeRate == null || feeRate.compareTo(BigDecimal.ZERO) < 0 || feeRate.compareTo(MAX_FEE_RATE) > 0) {
            throw new DomainValidationException("Transaction fee rate must be between 0 and 0.50 (0% - 50%)");
        }
        feeRate = feeRate.setScale(4, RoundingMode.HALF_UP);
        feePercent = feeRate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    public static TradingFeeSetting ofRate(BigDecimal feeRate, Long updatedByUserId, Instant updatedAt) {
        return new TradingFeeSetting(feeRate, null, updatedByUserId, updatedAt);
    }

    public static BigDecimal parsePercentToRate(BigDecimal feePercent) {
        if (feePercent == null || feePercent.compareTo(BigDecimal.ZERO) < 0
                || feePercent.compareTo(BigDecimal.valueOf(50)) > 0) {
            throw new DomainValidationException("Transaction fee percent must be between 0 and 50");
        }
        return feePercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }
}
