package com.carbonx.trading.infrastructure.persistence.entity;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.domain.order.OrderStatus;
import com.carbonx.trading.domain.order.TradeOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** JPA row of {@code trade_orders}. */
@Entity
@Table(name = "trade_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeOrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true, length = 64)
    private String orderCode;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "buyer_company_id", nullable = false)
    private Long buyerCompanyId;

    @Column(name = "seller_company_id", nullable = false)
    private Long sellerCompanyId;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "gross_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "platform_fee", nullable = false, precision = 18, scale = 4)
    private BigDecimal platformFee;

    @Column(name = "net_seller_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal netSellerAmount;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "settled_at", nullable = false)
    private Instant settledAt;

    public static TradeOrderJpaEntity empty() {
        return new TradeOrderJpaEntity();
    }

    public void apply(TradeOrder order) {
        this.orderCode = order.orderCode();
        this.idempotencyKey = order.idempotencyKey();
        this.listingId = order.listingId();
        this.batchId = order.batchId();
        this.buyerCompanyId = order.buyerCompanyId();
        this.sellerCompanyId = order.sellerCompanyId();
        this.quantity = order.quantity();
        this.unitPrice = order.unitPrice().amount();
        this.grossAmount = order.grossAmount().amount();
        this.platformFee = order.platformFee().amount();
        this.netSellerAmount = order.netSellerAmount().amount();
        this.status = order.status().name();
        this.settledAt = order.settledAt() == null ? Instant.now() : order.settledAt();
    }

    public TradeOrder toDomain() {
        return new TradeOrder(id, orderCode, idempotencyKey, listingId, batchId, buyerCompanyId, sellerCompanyId,
                quantity, Money.of(unitPrice), Money.of(grossAmount), Money.of(platformFee),
                Money.of(netSellerAmount), OrderStatus.valueOf(status), settledAt);
    }
}
