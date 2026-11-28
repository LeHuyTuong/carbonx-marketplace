package com.carbonx.registry.domain.credit;

import com.carbonx.common.domain.SerialRange;
import com.carbonx.registry.domain.shared.DomainValidationException;

import java.time.Instant;

/** One append-only entry of the serial ledger: what moved, how many serials and why. */
public record CreditMovement(
        Long id,
        Long holdingId,
        Long batchId,
        MovementType movementType,
        long quantity,
        long serialStart,
        long serialEnd,
        String referenceId,
        Instant createdAt
) {

    private static final int MAX_REFERENCE_LENGTH = 128;

    public CreditMovement {
        if (holdingId == null) {
            throw new DomainValidationException("holdingId is required");
        }
        if (batchId == null) {
            throw new DomainValidationException("batchId is required");
        }
        if (movementType == null) {
            throw new DomainValidationException("movementType is required");
        }
        if (quantity <= 0) {
            throw new DomainValidationException("A movement must move at least one credit");
        }
        if (serialStart <= 0 || serialEnd < serialStart) {
            throw new DomainValidationException(
                    "Invalid movement serial range: [" + serialStart + ", " + serialEnd + "]");
        }
        if (serialEnd - serialStart + 1 != quantity) {
            throw new DomainValidationException("Movement serial range must match the moved quantity");
        }
        referenceId = referenceId == null || referenceId.isBlank()
                ? null
                : referenceId.trim().substring(0, Math.min(referenceId.trim().length(), MAX_REFERENCE_LENGTH));
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static CreditMovement of(CreditHolding holding, MovementType type, long quantity, SerialRange range,
                                    String referenceId) {
        return new CreditMovement(null, holding.id(), holding.batchId(), type, quantity,
                range.start(), range.end(), referenceId, Instant.now());
    }

    public String serialRangeLabel() {
        return String.format("%06d..%06d", serialStart, serialEnd);
    }
}
