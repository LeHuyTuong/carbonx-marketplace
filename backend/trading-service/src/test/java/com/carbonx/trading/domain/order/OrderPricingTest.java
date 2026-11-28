package com.carbonx.trading.domain.order;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPricingTest {

    @Test
    void computesDefaultFivePercentAndDynamicFeeRatesCleanly() {
        OrderPricing defaultPricing = OrderPricing.of(Money.of(new BigDecimal("20.00")), 10L);
        assertThat(defaultPricing.grossAmount().amount()).isEqualByComparingTo("200.0000");
        assertThat(defaultPricing.platformFee().amount()).isEqualByComparingTo("10.0000");
        assertThat(defaultPricing.netSellerAmount().amount()).isEqualByComparingTo("190.0000");

        OrderPricing customPricing = OrderPricing.of(Money.of(new BigDecimal("20.00")), 10L, new BigDecimal("0.0350"));
        assertThat(customPricing.platformFee().amount()).isEqualByComparingTo("7.0000");
        assertThat(customPricing.netSellerAmount().amount()).isEqualByComparingTo("193.0000");
    }

    @Test
    void rejectsNegativeOrExcessiveFeeRates() {
        assertThatThrownBy(() -> OrderPricing.of(Money.of(new BigDecimal("10.00")), 1L, new BigDecimal("-0.01")))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> OrderPricing.of(Money.of(new BigDecimal("10.00")), 1L, new BigDecimal("0.51")))
                .isInstanceOf(DomainValidationException.class);
    }
}
