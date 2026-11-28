package com.carbonx.registry.infrastructure.persistence.adapter;

import com.carbonx.registry.application.port.out.CreditHoldingRepositoryPort;
import com.carbonx.registry.domain.credit.CreditHolding;
import com.carbonx.registry.infrastructure.persistence.entity.CreditHoldingJpaEntity;
import com.carbonx.registry.infrastructure.persistence.repository.CreditHoldingJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adapts the CreditHolding aggregate to {@code credit_holdings}. */
@Component
@RequiredArgsConstructor
public class CreditHoldingPersistenceAdapter implements CreditHoldingRepositoryPort {

    private final CreditHoldingJpaRepository repository;

    @Override
    public CreditHolding save(CreditHolding holding) {
        CreditHoldingJpaEntity entity;
        if (holding.id() == null) {
            entity = newEntity(holding);
        } else {
            entity = repository.findById(holding.id())
                    .orElseThrow(() -> new IllegalStateException("Holding " + holding.id() + " disappeared"));
            entity.applyBalances(holding.availableQty(), holding.lockedQty(), holding.retiredQty(),
                    holding.serialStart(), holding.serialEnd());
        }
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<CreditHolding> findById(Long id) {
        return repository.findById(id).map(CreditHoldingPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<CreditHolding> findByIdForUpdate(Long id) {
        return repository.lockById(id).map(CreditHoldingPersistenceAdapter::toDomain);
    }

    @Override
    public List<CreditHolding> findByHolderCompanyId(Long companyId) {
        return repository.findByHolderCompanyIdOrderByIdAsc(companyId).stream()
                .map(CreditHoldingPersistenceAdapter::toDomain)
                .toList();
    }

    private static CreditHoldingJpaEntity newEntity(CreditHolding holding) {
        return CreditHoldingJpaEntity.builder()
                .batchId(holding.batchId())
                .holderCompanyId(holding.holderCompanyId())
                .availableQty(holding.availableQty())
                .lockedQty(holding.lockedQty())
                .retiredQty(holding.retiredQty())
                .serialStart(holding.serialStart())
                .serialEnd(holding.serialEnd())
                .version(holding.version())
                .build();
    }

    private static CreditHolding toDomain(CreditHoldingJpaEntity entity) {
        return new CreditHolding(entity.getId(), entity.getBatchId(), entity.getHolderCompanyId(),
                entity.getAvailableQty(), entity.getLockedQty(), entity.getRetiredQty(),
                entity.getSerialStart(), entity.getSerialEnd(), entity.getVersion());
    }
}
