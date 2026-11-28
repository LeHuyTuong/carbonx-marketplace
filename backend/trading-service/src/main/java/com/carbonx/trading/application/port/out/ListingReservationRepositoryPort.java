package com.carbonx.trading.application.port.out;

import com.carbonx.trading.domain.order.ListingReservation;
import com.carbonx.trading.domain.order.ReservationStatus;

import java.util.List;
import java.util.Optional;

/** Persistence contract of {@code listing_reservations}. */
public interface ListingReservationRepositoryPort {

    ListingReservation save(ListingReservation reservation);

    Optional<ListingReservation> findById(Long id);

    Optional<ListingReservation> findByIdempotencyKey(String idempotencyKey);

    List<ListingReservation> findByListingIdAndStatus(Long listingId, ReservationStatus status);
}
