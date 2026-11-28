package com.carbonx.trading.infrastructure.persistence.repository;

import com.carbonx.trading.infrastructure.persistence.entity.ListingReservationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ListingReservationJpaRepository extends JpaRepository<ListingReservationJpaEntity, Long> {

    Optional<ListingReservationJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<ListingReservationJpaEntity> findByListingIdAndSagaState(Long listingId, String sagaState);
}
