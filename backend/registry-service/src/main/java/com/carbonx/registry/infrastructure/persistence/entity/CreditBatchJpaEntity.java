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

import java.math.BigDecimal;
import java.time.Instant;

/** JPA row of {@code credit_batches}. Insert-only: a batch is never updated after issuance. */
@Entity
@Table(name = "credit_batches")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditBatchJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_code", nullable = false, unique = true, length = 128)
    private String batchCode;

    @Column(name = "source_report_id", nullable = false, unique = true)
    private Long sourceReportId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "project_title_snap", nullable = false, length = 255)
    private String projectTitleSnap;

    @Column(name = "issuer_company_id", nullable = false)
    private Long issuerCompanyId;

    @Column(name = "vintage_year", nullable = false)
    private int vintageYear;

    @Column(name = "total_issued_qty", nullable = false)
    private long totalIssuedQty;

    @Column(name = "total_tco2e", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalTco2e;

    @Column(name = "residual_tco2e", nullable = false, precision = 18, scale = 4)
    private BigDecimal residualTco2e;

    @Column(name = "serial_prefix", nullable = false, length = 64)
    private String serialPrefix;

    @Column(name = "serial_start", nullable = false)
    private long serialStart;

    @Column(name = "serial_end", nullable = false)
    private long serialEnd;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
