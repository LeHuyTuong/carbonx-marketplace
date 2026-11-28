package com.carbonx.trading.application.port.in;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.domain.setting.TradingFeeSetting;

import java.time.Instant;
import java.util.List;

public interface TradingQueryUseCase {
    List<ListingView> listActiveMarketplace(Long projectIdFilter, String sortBy);
    List<ListingView> listMyListings(Long sellerCompanyId);
    List<OrderView> listMyOrders(Long buyerCompanyId);
    TradingFeeSetting getTradingFeeSetting();

    record ListingView(Long id, Long batchId, String batchCode, String projectTitle, Long sellerCompanyId, long availableQty, Money pricePerCredit, String status) {}
    record OrderView(Long id, String orderCode, Long batchId, long quantity, Money grossAmount, Money platformFee, String status, Instant settledAt) {}
}
