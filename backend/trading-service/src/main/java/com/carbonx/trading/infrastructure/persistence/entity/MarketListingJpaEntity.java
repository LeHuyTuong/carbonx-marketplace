package com.carbonx.trading.infrastructure.persistence.entity;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.domain.order.ListingStatus;
import com.carbonx.trading.domain.order.MarketListing;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** JPA row of {@code market_listings}; {@code version} gives the optimistic lock the adapter relies on. */
@Entity
@Table(name = "market_listings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketListingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holding_id", nullable = false)
    private Long holdingId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "batch_code_snap", nullable = false, length = 128)
    private String batchCodeSnap;

    @Column(name = "project_title_snap", nullable = false, length = 255)
    private String projectTitleSnap;

    @Column(name = "seller_company_id", nullable = false)
    private Long sellerCompanyId;

    @Column(name = "seller_user_id")
    private Long sellerUserId;

    @Column(name = "total_qty", nullable = false)
    private long totalQty;

    @Column(name = "available_qty", nullable = false)
    private long availableQty;

    @Column(name = "price_per_credit", nullable = false, precision = 18, scale = 4)
    private BigDecimal pricePerCredit;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public static MarketListingJpaEntity empty() {
        return new MarketListingJpaEntity();
    }

    public void apply(MarketListing listing) {
        this.holdingId = listing.holdingId();
        this.batchId = listing.batchId();
        this.batchCodeSnap = listing.batchCodeSnap();
        this.projectTitleSnap = listing.projectTitleSnap();
        this.sellerCompanyId = listing.sellerCompanyId();
        this.sellerUserId = listing.sellerUserId();
        this.totalQty = listing.totalQty();
        this.availableQty = listing.availableQty();
        this.pricePerCredit = listing.pricePerCredit().amount();
        this.status = listing.status().name();
        this.expiresAt = listing.expiresAt();
    }

    public MarketListing toDomain() {
        return new MarketListing(id, holdingId, batchId, batchCodeSnap, projectTitleSnap, sellerCompanyId,
                sellerUserId, totalQty, availableQty, Money.of(pricePerCredit), ListingStatus.valueOf(status),
                expiresAt, version == null ? 0L : version);
    }
}
