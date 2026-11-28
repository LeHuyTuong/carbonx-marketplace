package com.carbonx.identity.infrastructure.persistence.entity;

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

/** JPA row of {@code vehicles}; the plate number is globally unique across identity rows. */
@Entity
@Table(name = "vehicles")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ev_owner_id", nullable = false)
    private Long evOwnerId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "plate_number", nullable = false, unique = true, length = 32)
    private String plateNumber;

    @Column(name = "brand", nullable = false, length = 64)
    private String brand;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
