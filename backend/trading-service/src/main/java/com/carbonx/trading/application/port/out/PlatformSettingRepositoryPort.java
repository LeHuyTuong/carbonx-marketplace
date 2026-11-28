package com.carbonx.trading.application.port.out;

import com.carbonx.trading.domain.setting.TradingFeeSetting;

import java.math.BigDecimal;

/** Outbound port for persisted trading platform configuration such as transaction fee rates. */
public interface PlatformSettingRepositoryPort {
    TradingFeeSetting getTradingFeeSetting();

    default BigDecimal getTradingFeeRate() {
        return getTradingFeeSetting().feeRate();
    }

    TradingFeeSetting saveTradingFeeRate(Long adminUserId, BigDecimal feeRate);
}
