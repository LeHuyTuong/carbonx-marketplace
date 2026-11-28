package com.carbonx.trading.infrastructure.persistence.entity;

import com.carbonx.trading.domain.order.ListingReservation;
import com.carbonx.trading.domain.order.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** JPA row of {@code listing_reservations}. */
@Entity
@Table(name = "listing_reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListingReservationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "buyer_company_id", nullable = false)
    private Long buyerCompanyId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "saga_state", nullable = false, length = 32)
    private String sagaState;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public static ListingReservationJpaEntity empty() {
        return new ListingReservationJpaEntity();
    }

    public void apply(ListingReservation reservation) {
        this.listingId = reservation.listingId();
        this.buyerCompanyId = reservation.buyerCompanyId();
        this.idempotencyKey = reservation.idempotencyKey();
        this.quantity = reservation.quantity();
        this.sagaState = reservation.status().name();
        this.expiresAt = reservation.expiresAt();
    }

    public ListingReservation toDomain() {
        return new ListingReservation(id, listingId, buyerCompanyId, idempotencyKey, quantity,
                ReservationStatus.valueOf(sagaState), expiresAt, createdAt == null ? Instant.now() : createdAt);
    }
}
