package com.carbonx.trading.application.port.out;

import com.carbonx.trading.domain.order.TradeOrder;

import java.util.List;
import java.util.Optional;

/** Persistence contract of {@code trade_orders}. */
public interface TradeOrderRepositoryPort {

    TradeOrder save(TradeOrder order);

    Optional<TradeOrder> findById(Long id);

    Optional<TradeOrder> findByOrderCode(String orderCode);

    List<TradeOrder> findByBuyerCompanyId(Long buyerCompanyId);

    List<TradeOrder> findByListingId(Long listingId);
}
