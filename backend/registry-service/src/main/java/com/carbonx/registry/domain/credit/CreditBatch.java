package com.carbonx.registry.domain.credit;

import com.carbonx.common.domain.SerialRange;
import com.carbonx.registry.domain.shared.DomainValidationException;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable issuance of carbon credits for one approved emission report. A batch owns a contiguous
 * serial range; its parameters can never change after issuance — ownership changes are modelled by
 * {@link CreditHolding} rows so the origin stays auditable.
 */
public record CreditBatch(
        Long id,
        String batchCode,
        Long sourceReportId,
        Long projectId,
        String projectTitleSnap,
        Long issuerCompanyId,
        int vintageYear,
        long totalIssuedQty,
        BigDecimal totalTco2e,
        BigDecimal residualTco2e,
        String serialPrefix,
        long serialStart,
        long serialEnd,
        Instant issuedAt,
        Instant expiresAt
) {

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_CODE_LENGTH = 128;
    private static final int MAX_PREFIX_LENGTH = 64;

    public CreditBatch {
        if (sourceReportId == null) {
            throw new DomainValidationException("sourceReportId is required");
        }
        if (projectId == null) {
            throw new DomainValidationException("projectId is required");
        }
        if (issuerCompanyId == null) {
            throw new DomainValidationException("issuerCompanyId is required");
        }
        if (vintageYear < 1900 || vintageYear > 9999) {
            throw new DomainValidationException("vintageYear is out of range: " + vintageYear);
        }
        if (totalIssuedQty <= 0) {
            throw new DomainValidationException("A batch must issue at least one credit");
        }
        if (serialStart <= 0 || serialEnd < serialStart) {
            throw new DomainValidationException(
                    "Invalid batch serial range: [" + serialStart + ", " + serialEnd + "]");
        }
        if (serialEnd - serialStart + 1 != totalIssuedQty) {
            throw new DomainValidationException("Batch serial range must cover exactly totalIssuedQty credits");
        }
        if (totalTco2e == null || totalTco2e.signum() < 0) {
            throw new DomainValidationException("totalTco2e must be zero or positive");
        }
        totalTco2e = totalTco2e.setScale(4, java.math.RoundingMode.HALF_UP);
        residualTco2e = residualTco2e == null
                ? BigDecimal.ZERO.setScale(4)
                : residualTco2e.setScale(4, java.math.RoundingMode.HALF_UP);
        if (residualTco2e.signum() < 0) {
            throw new DomainValidationException("residualTco2e must be zero or positive");
        }
        if (residualTco2e.compareTo(totalTco2e) > 0) {
            throw new DomainValidationException("residualTco2e cannot exceed the adjusted tonnage");
        }
        batchCode = truncate(requireText(batchCode, "batchCode"), MAX_CODE_LENGTH);
        serialPrefix = truncate(requireText(serialPrefix, "serialPrefix"), MAX_PREFIX_LENGTH);
        projectTitleSnap = truncate(requireText(projectTitleSnap, "projectTitleSnap"), MAX_TITLE_LENGTH);
        issuedAt = issuedAt == null ? Instant.now() : issuedAt;
        if (expiresAt != null && expiresAt.isBefore(issuedAt)) {
            throw new DomainValidationException("expiresAt cannot precede issuedAt");
        }
    }

    /**
     * Mints a batch from an approved MRV report. {@code credits} must equal the serial range length
     * and {@code residualTco2e} is the fractional tonnage carried into the next period.
     */
    public static CreditBatch issue(String batchCode, Long sourceReportId, Long projectId, String projectTitle,
                                    Long issuerCompanyId, int vintageYear, BigDecimal totalTco2e,
                                    BigDecimal residualTco2e, String serialPrefix, SerialRange serials,
                                    Instant issuedAt, Instant expiresAt) {
        return new CreditBatch(null, batchCode, sourceReportId, projectId, projectTitle, issuerCompanyId,
                vintageYear, serials.quantity(), totalTco2e, residualTco2e, serialPrefix,
                serials.start(), serials.end(), issuedAt, expiresAt);
    }

    /** True when this batch was already minted for the same report — the consumer idempotency key. */
    public boolean belongsToReport(Long reportId) {
        return sourceReportId.equals(reportId);
    }

    public String serialRangeLabel() {
        return String.format("%06d..%06d", serialStart, serialEnd);
    }

    public CreditBatch withId(Long assignedId) {
        return new CreditBatch(assignedId, batchCode, sourceReportId, projectId, projectTitleSnap,
                issuerCompanyId, vintageYear, totalIssuedQty, totalTco2e, residualTco2e, serialPrefix,
                serialStart, serialEnd, issuedAt, expiresAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field + " is required");
        }
        return value.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
