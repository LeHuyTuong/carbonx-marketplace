package com.carbonx.mrv.infrastructure.persistence.entity;

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

import java.math.BigDecimal;
import java.time.Instant;

/** JPA row of {@code projects}. */
@Entity
@Table(name = "projects")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "emission_factor", nullable = false, precision = 12, scale = 6)
    private BigDecimal emissionFactor;

    @Column(name = "buffer_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal bufferRate;

    @Column(name = "uncertainty_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal uncertaintyRate;

    @Column(name = "leakage_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal leakageRate;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
