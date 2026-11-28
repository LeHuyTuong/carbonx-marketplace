package com.carbonx.registry.application.port.out;

import com.carbonx.registry.domain.credit.CreditMovement;
import com.carbonx.registry.domain.credit.MovementType;

import java.util.List;
import java.util.Optional;

/**
 * Append-only port of the serial ledger {@code credit_movements}. Rows are never updated or
 * deleted; the {@code referenceId} is the caller's idempotency key for saga steps.
 */
public interface CreditMovementRepositoryPort {

    CreditMovement save(CreditMovement movement);

    Optional<CreditMovement> findFirstByReferenceIdAndType(String referenceId, MovementType type);

    List<CreditMovement> findByHoldingId(Long holdingId);
}
