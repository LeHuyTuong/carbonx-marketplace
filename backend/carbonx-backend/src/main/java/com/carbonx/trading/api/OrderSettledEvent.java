package com.carbonx.trading.api;

import com.carbonx.shared.domain.DomainEvent;

import java.math.BigDecimal;

/** Domain Event published to Outbox when a 1-step instant TradeOrder settles successfully. */
public record OrderSettledEvent(
        Long orderId,
        String orderCode,
        Long listingId,
        Long batchId,
        Long buyerCompanyId,
        Long sellerCompanyId,
        long quantity,
        BigDecimal grossAmount,
        BigDecimal platformFee
) implements DomainEvent {
    @Override
    public String aggregateType() {
        return "TradeOrder";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(orderId);
    }

    @Override
    public String eventType() {
        return "trading.order.settled";
    }
}
