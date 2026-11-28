package com.carbonx.registry.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** JPA row of the append-only serial ledger {@code credit_movements}. */
@Entity
@Table(name = "credit_movements")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditMovementJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holding_id", nullable = false)
    private Long holdingId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "movement_type", nullable = false, length = 32)
    private String movementType;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "serial_start", nullable = false)
    private long serialStart;

    @Column(name = "serial_end", nullable = false)
    private long serialEnd;

    @Column(name = "reference_id", length = 128)
    private String referenceId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
