package com.carbonx.registry.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA row of {@code credit_holdings}. {@code version} makes the optimistic lock explicit in the
 * schema; the command service additionally takes a pessimistic row lock so two saga steps on the
 * same holding never interleave.
 */
@Entity
@Table(name = "credit_holdings")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditHoldingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "holder_company_id", nullable = false)
    private Long holderCompanyId;

    @Column(name = "available_qty", nullable = false)
    private long availableQty;

    @Column(name = "locked_qty", nullable = false)
    private long lockedQty;

    @Column(name = "retired_qty", nullable = false)
    private long retiredQty;

    @Column(name = "serial_start", nullable = false)
    private long serialStart;

    @Column(name = "serial_end", nullable = false)
    private long serialEnd;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    /** Applies a domain state transition onto the managed row. */
    public void applyBalances(long available, long locked, long retired, long start, long end) {
        this.availableQty = available;
        this.lockedQty = locked;
        this.retiredQty = retired;
        this.serialStart = start;
        this.serialEnd = end;
    }
}
