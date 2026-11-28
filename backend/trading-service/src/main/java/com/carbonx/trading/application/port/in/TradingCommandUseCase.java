package com.carbonx.trading.application.port.in;

import com.carbonx.common.domain.Money;
import java.time.Instant;

public interface TradingCommandUseCase {
    Long createListing(Long sellerCompanyId, Long holdingId, long quantity, Money pricePerCredit, Instant expiresAt);
    void cancelListing(Long sellerCompanyId, Long listingId);
    Long executeInstantBuyOrderSaga(String idempotencyKey, Long buyerCompanyId, Long buyerUserId, Long listingId, long quantity);
}
