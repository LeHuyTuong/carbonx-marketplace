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

/** JPA row of {@code emission_reports}. */
@Entity
@Table(name = "emission_reports")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmissionReportJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "period", nullable = false, length = 32)
    private String period;

    @Column(name = "total_energy_kwh", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalEnergyKwh;

    @Column(name = "total_co2_kg", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalCo2Kg;

    @Column(name = "vehicle_count", nullable = false)
    private int vehicleCount;

    @Column(name = "file_sha256", length = 64)
    private String fileSha256;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "verifier_id")
    private Long verifierId;

    @Column(name = "ai_pre_score", precision = 5, scale = 2)
    private BigDecimal aiPreScore;

    @Column(name = "ai_version", length = 64)
    private String aiVersion;

    @Column(name = "ai_pre_notes", columnDefinition = "text")
    private String aiPreNotes;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
