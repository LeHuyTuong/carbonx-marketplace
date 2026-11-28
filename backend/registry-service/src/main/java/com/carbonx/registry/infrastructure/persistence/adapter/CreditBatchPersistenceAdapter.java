package com.carbonx.registry.infrastructure.persistence.adapter;

import com.carbonx.registry.application.port.out.CreditBatchRepositoryPort;
import com.carbonx.registry.domain.credit.CreditBatch;
import com.carbonx.registry.infrastructure.persistence.entity.CreditBatchJpaEntity;
import com.carbonx.registry.infrastructure.persistence.repository.CreditBatchJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Adapts the immutable CreditBatch aggregate to {@code credit_batches}. */
@Component
@RequiredArgsConstructor
public class CreditBatchPersistenceAdapter implements CreditBatchRepositoryPort {

    private final CreditBatchJpaRepository repository;

    @Override
    public CreditBatch insert(CreditBatch batch) {
        if (batch.id() != null) {
            throw new IllegalStateException(
                    "credit_batches is append-only: batch " + batch.id() + " cannot be modified");
        }
        return toDomain(repository.save(toEntity(batch)));
    }

    @Override
    public Optional<CreditBatch> findById(Long id) {
        return repository.findById(id).map(CreditBatchPersistenceAdapter::toDomain);
    }

    @Override
    public List<CreditBatch> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return repository.findByIdIn(ids).stream()
                .map(CreditBatchPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<CreditBatch> findAllOrderedDesc() {
        return repository.findAllByOrderByIdDesc().stream()
                .map(CreditBatchPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<CreditBatch> findBySourceReportId(Long sourceReportId) {
        return repository.findBySourceReportId(sourceReportId).map(CreditBatchPersistenceAdapter::toDomain);
    }

    @Override
    public long highestAllocatedSerial(Long projectId, Long issuerCompanyId, int vintageYear) {
        return repository.lockScopeOrderBySerialEndDesc(projectId, issuerCompanyId, vintageYear).stream()
                .findFirst()
                .map(CreditBatchJpaEntity::getSerialEnd)
                .orElse(0L);
    }

    @Override
    public Optional<BigDecimal> findResidualOfLatestBatch(Long projectId, Long issuerCompanyId, int vintageYear) {
        return repository
                .findTopByProjectIdAndIssuerCompanyIdAndVintageYearOrderBySerialEndDesc(
                        projectId, issuerCompanyId, vintageYear)
                .map(CreditBatchJpaEntity::getResidualTco2e);
    }

    private static CreditBatch toDomain(CreditBatchJpaEntity entity) {
        return new CreditBatch(entity.getId(), entity.getBatchCode(), entity.getSourceReportId(),
                entity.getProjectId(), entity.getProjectTitleSnap(), entity.getIssuerCompanyId(),
                entity.getVintageYear(), entity.getTotalIssuedQty(), entity.getTotalTco2e(),
                entity.getResidualTco2e(), entity.getSerialPrefix(), entity.getSerialStart(),
                entity.getSerialEnd(), entity.getIssuedAt(), entity.getExpiresAt());
    }

    private static CreditBatchJpaEntity toEntity(CreditBatch batch) {
        return CreditBatchJpaEntity.builder()
                .batchCode(batch.batchCode())
                .sourceReportId(batch.sourceReportId())
                .projectId(batch.projectId())
                .projectTitleSnap(batch.projectTitleSnap())
                .issuerCompanyId(batch.issuerCompanyId())
                .vintageYear(batch.vintageYear())
                .totalIssuedQty(batch.totalIssuedQty())
                .totalTco2e(batch.totalTco2e())
                .residualTco2e(batch.residualTco2e())
                .serialPrefix(batch.serialPrefix())
                .serialStart(batch.serialStart())
                .serialEnd(batch.serialEnd())
                .issuedAt(batch.issuedAt())
                .expiresAt(batch.expiresAt())
                .build();
    }
}
