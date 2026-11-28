package com.carbonx.trading.domain.order;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.domain.shared.DomainValidationException;

import java.time.Instant;

/**
 * One credit purchase. Created as PENDING when the saga reserves the listing, FINISHED only after
 * payment settled and credits were transferred, CANCELLED when the saga compensated.
 */
public record TradeOrder(
        Long id,
        String orderCode,
        String idempotencyKey,
        Long listingId,
        Long batchId,
        Long buyerCompanyId,
        Long sellerCompanyId,
        long quantity,
        Money unitPrice,
        Money grossAmount,
        Money platformFee,
        Money netSellerAmount,
        OrderStatus status,
        Instant settledAt
) {

    public TradeOrder {
        if (orderCode == null || orderCode.isBlank()) {
            throw new DomainValidationException("orderCode is required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new DomainValidationException("idempotencyKey is required");
        }
        if (listingId == null || batchId == null || buyerCompanyId == null || sellerCompanyId == null) {
            throw new DomainValidationException("listingId, batchId, buyerCompanyId and sellerCompanyId are required");
        }
        if (quantity <= 0) {
            throw new DomainValidationException("quantity must be positive");
        }
        if (!grossAmount.equals(unitPrice.multiply(quantity))) {
            throw new DomainValidationException("grossAmount must equal unitPrice multiplied by quantity");
        }
        if (!platformFee.add(netSellerAmount).equals(grossAmount)) {
            throw new DomainValidationException("platformFee plus netSellerAmount must equal grossAmount");
        }
        if (status == null) {
            throw new DomainValidationException("status is required");
        }
    }

    public static TradeOrder place(String orderCode, String idempotencyKey, MarketListing listing,
                                   OrderPricing pricing, Long buyerCompanyId) {
        return new TradeOrder(null, orderCode, idempotencyKey, listing.id(), listing.batchId(),
                buyerCompanyId, listing.sellerCompanyId(), pricing.quantity(), pricing.unitPrice(),
                pricing.grossAmount(), pricing.platformFee(), pricing.netSellerAmount(), OrderStatus.PENDING, null);
    }

    public TradeOrder withId(Long newId) {
        return new TradeOrder(newId, orderCode, idempotencyKey, listingId, batchId, buyerCompanyId,
                sellerCompanyId, quantity, unitPrice, grossAmount, platformFee, netSellerAmount, status, settledAt);
    }

    public TradeOrder finish(Instant when) {
        if (status == OrderStatus.FINISHED) {
            return this;
        }
        if (status == OrderStatus.CANCELLED) {
            throw new DomainValidationException("Order " + orderCode + " is cancelled and cannot be finished");
        }
        return new TradeOrder(id, orderCode, idempotencyKey, listingId, batchId, buyerCompanyId, sellerCompanyId,
                quantity, unitPrice, grossAmount, platformFee, netSellerAmount, OrderStatus.FINISHED, when);
    }

    public TradeOrder cancel() {
        if (status == OrderStatus.CANCELLED) {
            return this;
        }
        if (status == OrderStatus.FINISHED) {
            throw new DomainValidationException("Order " + orderCode + " is already finished and cannot be cancelled");
        }
        return new TradeOrder(id, orderCode, idempotencyKey, listingId, batchId, buyerCompanyId, sellerCompanyId,
                quantity, unitPrice, grossAmount, platformFee, netSellerAmount, OrderStatus.CANCELLED, settledAt);
    }

    public boolean belongsToBuyer(Long companyId) {
        return buyerCompanyId != null && buyerCompanyId.equals(companyId);
    }
}
