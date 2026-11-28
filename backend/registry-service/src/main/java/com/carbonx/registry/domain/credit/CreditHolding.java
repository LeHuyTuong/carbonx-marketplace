package com.carbonx.registry.domain.credit;

import com.carbonx.common.domain.SerialRange;
import com.carbonx.registry.domain.shared.DomainValidationException;
import com.carbonx.registry.domain.shared.InsufficientCreditsException;

/**
 * Credits of one batch owned by one company. Issuance creates a single holding with the whole
 * serial range; the 80/20 split is the outcome of the marketplace flow: the credits sold are
 * locked at the head of the range and transferred to a buyer holding, while the tail stays with the
 * issuer for retirement ({@code 000001..000080} sold, {@code 000081..000100} retained).
 *
 * <p>Serial layout invariant: the locked block always sits at the head of the span and the
 * available block directly after it, so every operation keeps a contiguous, auditable range.</p>
 */
public record CreditHolding(
        Long id,
        Long batchId,
        Long holderCompanyId,
        long availableQty,
        long lockedQty,
        long retiredQty,
        long serialStart,
        long serialEnd,
        Long version
) {

    public CreditHolding {
        if (batchId == null) {
            throw new DomainValidationException("batchId is required");
        }
        if (holderCompanyId == null) {
            throw new DomainValidationException("holderCompanyId is required");
        }
        if (availableQty < 0 || lockedQty < 0 || retiredQty < 0) {
            throw new DomainValidationException("Holding quantities can never be negative");
        }
        if (serialStart <= 0 || serialEnd < serialStart) {
            throw new DomainValidationException(
                    "Invalid holding serial range: [" + serialStart + ", " + serialEnd + "]");
        }
        if (availableQty + lockedQty > serialEnd - serialStart + 1) {
            throw new DomainValidationException("Holding quantities exceed the owned serial range");
        }
        version = version == null ? 0L : version;
    }

    /** Mints the issuer holding for a freshly issued batch: every serial is available. */
    public static CreditHolding issue(Long batchId, Long holderCompanyId, long quantity, long serialStart) {
        if (quantity <= 0) {
            throw new DomainValidationException("A holding must receive at least one credit");
        }
        long serialEnd;
        try {
            serialEnd = Math.addExact(serialStart, quantity - 1);
        } catch (ArithmeticException ex) {
            throw new DomainValidationException("Serial range overflow for quantity " + quantity);
        }
        return new CreditHolding(null, batchId, holderCompanyId, quantity, 0, 0, serialStart, serialEnd, 0L);
    }

    /** Reserves credits for one listing; they remain owned (and locked) by the seller. */
    public CreditHolding lock(long quantity) {
        requirePositive(quantity);
        if (quantity > availableQty) {
            throw new InsufficientCreditsException("Holding " + id + " has only " + availableQty
                    + " available credits, cannot lock " + quantity);
        }
        return new CreditHolding(id, batchId, holderCompanyId, availableQty - quantity, lockedQty + quantity,
                retiredQty, serialStart, serialEnd, version);
    }

    /** Undoes a reservation, e.g. when the Order Saga compensates a failed payment. */
    public CreditHolding release(long quantity) {
        requirePositive(quantity);
        if (quantity > lockedQty) {
            throw new InsufficientCreditsException("Holding " + id + " has only " + lockedQty
                    + " locked credits, cannot release " + quantity);
        }
        return new CreditHolding(id, batchId, holderCompanyId, availableQty + quantity, lockedQty - quantity,
                retiredQty, serialStart, serialEnd, version);
    }

    /**
     * Removes {@code quantity} credits from the head of the locked block because they were sold.
     * The consumed serials are the returned range's.
     */
    public CreditHolding transferLocked(long quantity) {
        requirePositive(quantity);
        if (quantity > lockedQty) {
            throw new InsufficientCreditsException("Holding " + id + " has only " + lockedQty
                    + " locked credits, cannot transfer " + quantity);
        }
        long remaining = availableQty + (lockedQty - quantity);
        long newStart = remaining == 0 ? serialStart : Math.addExact(serialStart, quantity);
        return new CreditHolding(id, batchId, holderCompanyId, availableQty, lockedQty - quantity, retiredQty,
                newStart, serialEnd, version);
    }

    /** Permanently cancels credits from the tail of the available block. */
    public CreditHolding retire(long quantity) {
        requirePositive(quantity);
        if (quantity > availableQty) {
            throw new InsufficientCreditsException("Holding " + id + " has only " + availableQty
                    + " available credits, cannot retire " + quantity);
        }
        long remaining = (availableQty - quantity) + lockedQty;
        long newEnd = remaining == 0 ? serialEnd : serialEnd - quantity;
        return new CreditHolding(id, batchId, holderCompanyId, availableQty - quantity, lockedQty,
                retiredQty + quantity, serialStart, newEnd, version);
    }

    /** Serial range of the locked block; valid only while {@code lockedQty > 0}. */
    public SerialRange lockedSerialRange() {
        if (lockedQty <= 0) {
            throw new DomainValidationException("Holding " + id + " has no locked credits");
        }
        return new SerialRange(serialStart, serialStart + lockedQty - 1);
    }

    /** Serial range of the available block; valid only while {@code availableQty > 0}. */
    public SerialRange availableSerialRange() {
        if (availableQty <= 0) {
            throw new DomainValidationException("Holding " + id + " has no available credits");
        }
        return new SerialRange(serialStart + lockedQty, serialEnd);
    }

    /** Serial range of the {@code quantity} credits a retirement will consume (tail of available). */
    public SerialRange retirementRange(long quantity) {
        requirePositive(quantity);
        if (quantity > availableQty) {
            throw new InsufficientCreditsException("Holding " + id + " has only " + availableQty
                    + " available credits, cannot retire " + quantity);
        }
        return new SerialRange(serialEnd - quantity + 1, serialEnd);
    }

    public boolean owns(Long companyId) {
        return holderCompanyId.equals(companyId);
    }

    public String serialRangeLabel() {
        return String.format("%06d..%06d", serialStart, serialEnd);
    }

    private static void requirePositive(long quantity) {
        if (quantity <= 0) {
            throw new DomainValidationException("Quantity must be positive");
        }
    }
}
