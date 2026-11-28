package com.carbonx.registry.domain.credit;

import com.carbonx.registry.domain.shared.DomainValidationException;

import java.time.Instant;

/** Proof that a serial range was permanently retired for a beneficiary. */
public record RetirementCertificate(
        Long id,
        String certificateCode,
        Long holdingId,
        Long batchId,
        Long holderCompanyId,
        long retiredQty,
        long serialStart,
        long serialEnd,
        String beneficiaryName,
        String reason,
        String pdfUrl,
        Instant retiredAt
) {

    private static final int MAX_CODE_LENGTH = 128;
    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_REASON_LENGTH = 500;

    public RetirementCertificate {
        if (holdingId == null || batchId == null || holderCompanyId == null) {
            throw new DomainValidationException("holdingId, batchId and holderCompanyId are required");
        }
        if (certificateCode == null || certificateCode.isBlank()) {
            throw new DomainValidationException("certificateCode is required");
        }
        certificateCode = truncate(certificateCode.trim(), MAX_CODE_LENGTH);
        if (retiredQty <= 0) {
            throw new DomainValidationException("A certificate must retire at least one credit");
        }
        if (serialStart <= 0 || serialEnd < serialStart || serialEnd - serialStart + 1 != retiredQty) {
            throw new DomainValidationException("Certificate serial range must match the retired quantity");
        }
        beneficiaryName = truncate(beneficiaryName, MAX_NAME_LENGTH);
        reason = truncate(reason, MAX_REASON_LENGTH);
        retiredAt = retiredAt == null ? Instant.now() : retiredAt;
    }

    public static RetirementCertificate issue(String certificateCode, CreditHolding holding, long quantity,
                                              long serialStart, long serialEnd, String beneficiaryName,
                                              String reason) {
        return new RetirementCertificate(null, certificateCode, holding.id(), holding.batchId(),
                holding.holderCompanyId(), quantity, serialStart, serialEnd, beneficiaryName, reason, null,
                Instant.now());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : (trimmed.length() <= max ? trimmed : trimmed.substring(0, max));
    }
}
