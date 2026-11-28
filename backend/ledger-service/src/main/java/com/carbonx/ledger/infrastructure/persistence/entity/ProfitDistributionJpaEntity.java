package com.carbonx.ledger.infrastructure.persistence.entity;

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

@Entity
@Table(name = "profit_distributions")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfitDistributionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "source_report_id", nullable = false, unique = true)
    private Long sourceReportId;

    @Column(name = "credit_batch_id", nullable = false)
    private Long creditBatchId;

    @Column(name = "batch_code_snap", nullable = false, length = 128)
    private String batchCodeSnap;

    @Column(name = "period", nullable = false, length = 32)
    private String period;

    @Column(name = "unit_price_per_credit", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPricePerCredit;

    @Column(name = "total_payout_usd", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalPayoutUsd;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
