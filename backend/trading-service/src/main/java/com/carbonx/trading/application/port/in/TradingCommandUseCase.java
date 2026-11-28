package com.carbonx.trading.application.port.in;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.domain.setting.TradingFeeSetting;

import java.math.BigDecimal;
import java.time.Instant;

public interface TradingCommandUseCase {
    Long createListing(Long sellerCompanyId, Long holdingId, long quantity, Money pricePerCredit, Instant expiresAt);

    default Long createListing(
            Long sellerCompanyId,
            Long sellerUserId,
            Long holdingId,
            long quantity,
            Money pricePerCredit,
            Instant expiresAt
    ) {
        return createListing(sellerCompanyId, holdingId, quantity, pricePerCredit, expiresAt);
    }

    void cancelListing(Long sellerCompanyId, Long listingId);
    Long executeInstantBuyOrderSaga(String idempotencyKey, Long buyerCompanyId, Long buyerUserId, Long listingId, long quantity);

    TradingFeeSetting updateTradingFeeRate(Long adminUserId, BigDecimal feeRate);
}
