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

/** JPA row of {@code kyc_profiles} (one dossier per EV owner). */
@Entity
@Table(name = "kyc_profiles")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KycProfileJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ev_owner_id", nullable = false, unique = true)
    private Long evOwnerId;

    @Column(name = "full_name", length = 191)
    private String fullName;

    @Column(name = "document_type", nullable = false, length = 32)
    private String documentType;

    @Column(name = "document_number", nullable = false, length = 64)
    private String documentNumber;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "country", length = 128)
    private String country;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "birth_date", length = 32)
    private String birthDate;

    @Column(name = "gender", length = 16)
    private String gender;

    @Column(name = "kyc_status", nullable = false, length = 32)
    private String kycStatus;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
