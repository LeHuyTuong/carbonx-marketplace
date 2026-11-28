package com.carbonx.trading.domain.order;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.domain.shared.DomainValidationException;
import com.carbonx.trading.domain.shared.ListingUnavailableException;

import java.time.Instant;

/**
 * A seller's offer of credits from one registry holding. Quantity is reserved the moment the Order
 * Saga starts and only committed once every saga step succeeded; a compensation restores it.
 *
 * <p>The projection (batch code, project title) is a snapshot taken when the seller lists the
 * credits, so the marketplace can render without a synchronous call to registry-service.</p>
 */
public record MarketListing(
        Long id,
        Long holdingId,
        Long batchId,
        String batchCodeSnap,
        String projectTitleSnap,
        Long sellerCompanyId,
        Long sellerUserId,
        long totalQty,
        long availableQty,
        Money pricePerCredit,
        ListingStatus status,
        Instant expiresAt,
        Long version
) {

    public MarketListing(Long id, Long holdingId, Long batchId, String batchCodeSnap, String projectTitleSnap,
                         Long sellerCompanyId, long totalQty, long availableQty, Money pricePerCredit,
                         ListingStatus status, Instant expiresAt, Long version) {
        this(id, holdingId, batchId, batchCodeSnap, projectTitleSnap, sellerCompanyId, sellerCompanyId,
                totalQty, availableQty, pricePerCredit, status, expiresAt, version);
    }

    public MarketListing {
        if (holdingId == null || batchId == null || sellerCompanyId == null) {
            throw new DomainValidationException("holdingId, batchId and sellerCompanyId are required");
        }
        if (batchCodeSnap == null || batchCodeSnap.isBlank()) {
            throw new DomainValidationException("batchCode is required");
        }
        if (projectTitleSnap == null || projectTitleSnap.isBlank()) {
            throw new DomainValidationException("projectTitle is required");
        }
        if (totalQty <= 0 || availableQty < 0 || availableQty > totalQty) {
            throw new DomainValidationException("Invalid listing quantities: total " + totalQty
                    + ", available " + availableQty);
        }
        if (pricePerCredit == null || pricePerCredit.isNegative() || pricePerCredit.amount().signum() == 0) {
            throw new DomainValidationException("pricePerCredit must be positive");
        }
        if (status == null) {
            throw new DomainValidationException("status is required");
        }
        version = version == null ? 0L : version;
    }

    public static MarketListing list(Long holdingId, Long batchId, String batchCodeSnap, String projectTitleSnap,
                                      Long sellerCompanyId, long quantity, Money pricePerCredit, Instant expiresAt) {
        return list(holdingId, batchId, batchCodeSnap, projectTitleSnap, sellerCompanyId, sellerCompanyId,
                quantity, pricePerCredit, expiresAt);
    }

    public static MarketListing list(Long holdingId, Long batchId, String batchCodeSnap, String projectTitleSnap,
                                      Long sellerCompanyId, Long sellerUserId, long quantity,
                                      Money pricePerCredit, Instant expiresAt) {
        Long resolvedSellerUserId = sellerUserId != null ? sellerUserId : sellerCompanyId;
        return new MarketListing(null, holdingId, batchId, batchCodeSnap, projectTitleSnap, sellerCompanyId,
                resolvedSellerUserId, quantity, quantity, pricePerCredit, ListingStatus.LISTED, expiresAt, 0L);
    }

    public MarketListing withId(Long newId) {
        return new MarketListing(newId, holdingId, batchId, batchCodeSnap, projectTitleSnap, sellerCompanyId,
                sellerUserId, totalQty, availableQty, pricePerCredit, status, expiresAt, version);
    }

    public Long effectiveSellerUserId() {
        return sellerUserId != null ? sellerUserId : sellerCompanyId;
    }

    public boolean isOwnedBy(Long companyId) {
        return sellerCompanyId.equals(companyId);
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /** Holds {@code quantity} credits for one order; the quantity leaves the sellable pool. */
    public MarketListing reserve(long quantity, Instant now) {
        requirePositive(quantity);
        if (isExpired(now)) {
            throw new ListingUnavailableException("Listing " + id + " expired at " + expiresAt);
        }
        if (!status.isPurchasable()) {
            throw new ListingUnavailableException("Listing " + id + " is " + status + ", it cannot be bought");
        }
        if (quantity > availableQty) {
            throw new ListingUnavailableException("Listing " + id + " has only " + availableQty
                    + " available credits, cannot reserve " + quantity);
        }
        long remaining = availableQty - quantity;
        return new MarketListing(id, holdingId, batchId, batchCodeSnap, projectTitleSnap, sellerCompanyId,
                sellerUserId, totalQty, remaining, pricePerCredit,
                remaining == 0 ? ListingStatus.SOLD_OUT : status, expiresAt, version);
    }

    /** Gives reserved quantity back when the Order Saga compensates. */
    public MarketListing restore(long quantity) {
        requirePositive(quantity);
        long restored = Math.min(totalQty, availableQty + quantity);
        return new MarketListing(id, holdingId, batchId, batchCodeSnap, projectTitleSnap, sellerCompanyId,
                sellerUserId, totalQty, restored, pricePerCredit,
                status == ListingStatus.SOLD_OUT && restored > 0 ? ListingStatus.LISTED : status, expiresAt, version);
    }

    public MarketListing cancel() {
        if (status == ListingStatus.CANCELLED) {
            return this;
        }
        return new MarketListing(id, holdingId, batchId, batchCodeSnap, projectTitleSnap, sellerCompanyId,
                sellerUserId, totalQty, availableQty, pricePerCredit, ListingStatus.CANCELLED, expiresAt, version);
    }

    private static void requirePositive(long quantity) {
        if (quantity <= 0) {
            throw new DomainValidationException("Quantity must be positive");
        }
    }
}
