package com.carbonx.trading.application.port.in;

import com.carbonx.common.domain.Money;
import java.time.Instant;
import java.util.List;

public interface TradingQueryUseCase {
    List<ListingView> listActiveMarketplace(Long projectIdFilter, String sortBy);
    List<OrderView> listMyOrders(Long buyerCompanyId);

    record ListingView(Long id, Long batchId, String batchCode, String projectTitle, Long sellerCompanyId, long availableQty, Money pricePerCredit, String status) {}
    record OrderView(Long id, String orderCode, Long batchId, long quantity, Money grossAmount, Money platformFee, String status, Instant settledAt) {}
}
