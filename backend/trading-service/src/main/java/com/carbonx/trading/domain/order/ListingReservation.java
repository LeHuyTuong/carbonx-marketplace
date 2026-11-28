package com.carbonx.trading.domain.order;

import com.carbonx.trading.domain.shared.DomainValidationException;

import java.time.Instant;

/** Quantity of one listing held for one buyer while the Order Saga executes. */
public record ListingReservation(
        Long id,
        Long listingId,
        Long buyerCompanyId,
        String idempotencyKey,
        long quantity,
        ReservationStatus status,
        Instant expiresAt,
        Instant createdAt
) {

    public ListingReservation {
        if (listingId == null || buyerCompanyId == null) {
            throw new DomainValidationException("listingId and buyerCompanyId are required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new DomainValidationException("idempotencyKey is required");
        }
        if (quantity <= 0) {
            throw new DomainValidationException("reservation quantity must be positive");
        }
        if (expiresAt == null) {
            throw new DomainValidationException("reservation expiresAt is required");
        }
        if (status == null) {
            throw new DomainValidationException("reservation status is required");
        }
    }

    public static ListingReservation reserve(Long listingId, Long buyerCompanyId, String idempotencyKey,
                                             long quantity, Instant expiresAt) {
        return new ListingReservation(null, listingId, buyerCompanyId, idempotencyKey, quantity,
                ReservationStatus.RESERVED, expiresAt, Instant.now());
    }

    public ListingReservation withId(Long newId) {
        return new ListingReservation(newId, listingId, buyerCompanyId, idempotencyKey, quantity, status,
                expiresAt, createdAt);
    }

    public ListingReservation commit() {
        if (status == ReservationStatus.COMMITTED) {
            return this;
        }
        if (status == ReservationStatus.CANCELLED) {
            throw new DomainValidationException("Reservation for order key " + idempotencyKey + " was cancelled");
        }
        return new ListingReservation(id, listingId, buyerCompanyId, idempotencyKey, quantity,
                ReservationStatus.COMMITTED, expiresAt, createdAt);
    }

    public ListingReservation cancel() {
        return new ListingReservation(id, listingId, buyerCompanyId, idempotencyKey, quantity,
                ReservationStatus.CANCELLED, expiresAt, createdAt);
    }
}
