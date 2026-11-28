package com.carbonx.registry.infrastructure.persistence.repository;

import com.carbonx.registry.infrastructure.persistence.entity.CreditMovementJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreditMovementJpaRepository extends JpaRepository<CreditMovementJpaEntity, Long> {

    Optional<CreditMovementJpaEntity> findFirstByReferenceIdAndMovementTypeOrderByIdAsc(
            String referenceId, String movementType);

    List<CreditMovementJpaEntity> findByHoldingIdOrderByIdAsc(Long holdingId);
}
