package com.carbonx.trading.infrastructure.persistence.adapter;

import com.carbonx.trading.application.port.out.ListingReservationRepositoryPort;
import com.carbonx.trading.domain.order.ListingReservation;
import com.carbonx.trading.domain.order.ReservationStatus;
import com.carbonx.trading.infrastructure.persistence.entity.ListingReservationJpaEntity;
import com.carbonx.trading.infrastructure.persistence.repository.ListingReservationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adapts the ListingReservation aggregate to {@code listing_reservations}. */
@Component
@RequiredArgsConstructor
public class ListingReservationPersistenceAdapter implements ListingReservationRepositoryPort {

    private final ListingReservationJpaRepository repository;

    @Override
    public ListingReservation save(ListingReservation reservation) {
        ListingReservationJpaEntity entity;
        if (reservation.id() == null) {
            entity = ListingReservationJpaEntity.empty();
        } else {
            entity = repository.findById(reservation.id())
                    .orElseThrow(() -> new IllegalStateException("Reservation " + reservation.id() + " disappeared"));
        }
        entity.apply(reservation);
        return repository.save(entity).toDomain();
    }

    @Override
    public Optional<ListingReservation> findById(Long id) {
        return repository.findById(id).map(ListingReservationJpaEntity::toDomain);
    }

    @Override
    public Optional<ListingReservation> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(ListingReservationJpaEntity::toDomain);
    }

    @Override
    public List<ListingReservation> findByListingIdAndStatus(Long listingId, ReservationStatus status) {
        return repository.findByListingIdAndSagaState(listingId, status.name()).stream()
                .map(ListingReservationJpaEntity::toDomain)
                .toList();
    }
}
