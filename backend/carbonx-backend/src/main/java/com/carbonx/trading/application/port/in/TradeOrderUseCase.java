package com.carbonx.trading.application.port.in;

import com.carbonx.shared.domain.Money;

import java.time.Instant;
import java.util.List;

/**
 * Inbound Use-Case Port: 1-Step Instant Trade Order Settlement (MASTER-PLAN.md Layer 1).
 * Locks listing (FOR UPDATE), settles wallet via LedgerSettlementPort, transfers CreditHolding via RegistryCommandPort,
 * and appends OrderSettledEvent to OutboxPort in one idempotent flow.
 */
public interface TradeOrderUseCase {
    TradeOrderView buyCreditsInstantly(Long buyerCompanyId, Long buyerUserId, BuyCreditsCommand command);

    List<TradeOrderView> listMyOrders(Long buyerCompanyId);

    TradeOrderView getOrderDetail(Long actorCompanyId, Long orderId);

    record BuyCreditsCommand(String idempotencyKey, Long listingId, long quantity) {}

    record TradeOrderView(
            Long id,
            String orderCode,
            Long listingId,
            Long batchId,
            Long buyerCompanyId,
            Long sellerCompanyId,
            long quantity,
            Money unitPrice,
            Money grossAmount,
            Money platformFee,
            Money netSellerAmount,
            String status,
            Instant settledAt
    ) {}
}
