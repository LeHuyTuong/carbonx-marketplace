package com.carbonx.trading.infrastructure.persistence.adapter;

import com.carbonx.trading.application.port.out.PlatformSettingRepositoryPort;
import com.carbonx.trading.domain.setting.TradingFeeSetting;
import com.carbonx.trading.infrastructure.persistence.entity.PlatformSettingJpaEntity;
import com.carbonx.trading.infrastructure.persistence.repository.PlatformSettingJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Reads and updates {@code trading.fee_rate} in {@code platform_settings}. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformSettingPersistenceAdapter implements PlatformSettingRepositoryPort {

    public static final String TRADING_FEE_RATE_KEY = "trading.fee_rate";
    private static final String FEE_DESCRIPTION = "Platform transaction commission rate";

    private final PlatformSettingJpaRepository repository;

    @Override
    public TradingFeeSetting getTradingFeeSetting() {
        return repository.findById(TRADING_FEE_RATE_KEY)
                .map(PlatformSettingPersistenceAdapter::toDomain)
                .orElseGet(() -> TradingFeeSetting.ofRate(TradingFeeSetting.DEFAULT_FEE_RATE, null, null));
    }

    @Override
    public TradingFeeSetting saveTradingFeeRate(Long adminUserId, BigDecimal feeRate) {
        TradingFeeSetting validated = TradingFeeSetting.ofRate(feeRate, adminUserId, null);
        PlatformSettingJpaEntity entity = repository.findById(TRADING_FEE_RATE_KEY)
                .orElseGet(() -> PlatformSettingJpaEntity.feeRate(
                        TRADING_FEE_RATE_KEY,
                        validated.feeRate().toPlainString(),
                        FEE_DESCRIPTION,
                        adminUserId));
        entity.updateValue(validated.feeRate().toPlainString(), adminUserId);
        PlatformSettingJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    private static TradingFeeSetting toDomain(PlatformSettingJpaEntity entity) {
        try {
            BigDecimal rate = new BigDecimal(entity.getSettingValue().trim());
            return TradingFeeSetting.ofRate(rate, entity.getUpdatedByUserId(), entity.getUpdatedAt());
        } catch (RuntimeException ex) {
            log.warn("Invalid stored {}='{}'; falling back to default {}", TRADING_FEE_RATE_KEY,
                    entity.getSettingValue(), TradingFeeSetting.DEFAULT_FEE_RATE);
            return TradingFeeSetting.ofRate(TradingFeeSetting.DEFAULT_FEE_RATE,
                    entity.getUpdatedByUserId(), entity.getUpdatedAt());
        }
    }
}
