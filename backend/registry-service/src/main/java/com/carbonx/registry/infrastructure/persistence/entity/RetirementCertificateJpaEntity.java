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

/** JPA row of {@code retirement_certificates}. */
@Entity
@Table(name = "retirement_certificates")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RetirementCertificateJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "certificate_code", nullable = false, unique = true, length = 128)
    private String certificateCode;

    @Column(name = "holding_id", nullable = false)
    private Long holdingId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "holder_company_id", nullable = false)
    private Long holderCompanyId;

    @Column(name = "retired_qty", nullable = false)
    private long retiredQty;

    @Column(name = "serial_start", nullable = false)
    private long serialStart;

    @Column(name = "serial_end", nullable = false)
    private long serialEnd;

    @Column(name = "beneficiary_name", length = 255)
    private String beneficiaryName;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Column(name = "retired_at", nullable = false)
    private Instant retiredAt;
}
