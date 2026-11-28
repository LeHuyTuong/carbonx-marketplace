package com.carbonx.registry.infrastructure.persistence.adapter;

import com.carbonx.registry.application.port.out.CreditMovementRepositoryPort;
import com.carbonx.registry.domain.credit.CreditMovement;
import com.carbonx.registry.domain.credit.MovementType;
import com.carbonx.registry.infrastructure.persistence.entity.CreditMovementJpaEntity;
import com.carbonx.registry.infrastructure.persistence.repository.CreditMovementJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adapts the append-only serial ledger to {@code credit_movements}. */
@Component
@RequiredArgsConstructor
public class CreditMovementPersistenceAdapter implements CreditMovementRepositoryPort {

    private final CreditMovementJpaRepository repository;

    @Override
    public CreditMovement save(CreditMovement movement) {
        if (movement.id() != null) {
            throw new IllegalStateException("credit_movements is append-only");
        }
        return toDomain(repository.save(CreditMovementJpaEntity.builder()
                .holdingId(movement.holdingId())
                .batchId(movement.batchId())
                .movementType(movement.movementType().name())
                .quantity(movement.quantity())
                .serialStart(movement.serialStart())
                .serialEnd(movement.serialEnd())
                .referenceId(movement.referenceId())
                .build()));
    }

    @Override
    public Optional<CreditMovement> findFirstByReferenceIdAndType(String referenceId, MovementType type) {
        return repository.findFirstByReferenceIdAndMovementTypeOrderByIdAsc(referenceId, type.name())
                .map(CreditMovementPersistenceAdapter::toDomain);
    }

    @Override
    public List<CreditMovement> findByHoldingId(Long holdingId) {
        return repository.findByHoldingIdOrderByIdAsc(holdingId).stream()
                .map(CreditMovementPersistenceAdapter::toDomain)
                .toList();
    }

    private static CreditMovement toDomain(CreditMovementJpaEntity entity) {
        return new CreditMovement(entity.getId(), entity.getHoldingId(), entity.getBatchId(),
                MovementType.valueOf(entity.getMovementType()), entity.getQuantity(), entity.getSerialStart(),
                entity.getSerialEnd(), entity.getReferenceId(), entity.getCreatedAt());
    }
}
