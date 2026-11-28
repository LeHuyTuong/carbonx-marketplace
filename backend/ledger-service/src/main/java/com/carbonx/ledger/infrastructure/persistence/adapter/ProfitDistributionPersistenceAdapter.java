package com.carbonx.ledger.infrastructure.persistence.adapter;

import com.carbonx.ledger.application.port.out.ProfitDistributionRepositoryPort;
import com.carbonx.ledger.domain.wallet.ProfitDistribution;
import com.carbonx.ledger.infrastructure.persistence.entity.ProfitDistributionJpaEntity;
import com.carbonx.ledger.infrastructure.persistence.repository.ProfitDistributionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProfitDistributionPersistenceAdapter implements ProfitDistributionRepositoryPort {

    private final ProfitDistributionJpaRepository repository;

    @Override
    public Optional<ProfitDistribution> findByIdempotencyKey(String idempotencyKey) {
        return Optional.empty();
    }

    @Override
    public List<ProfitDistribution> findByCompanyIdOrdered(Long companyId) {
        return repository.findByCompanyIdOrderByIdDesc(companyId).stream()
                .map(ProfitDistributionPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<ProfitDistribution> findAllOrdered() {
        return repository.findAllByOrderByIdDesc().stream()
                .map(ProfitDistributionPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public ProfitDistribution save(ProfitDistribution d) {
        Optional<ProfitDistributionJpaEntity> existing = repository.findBySourceReportId(d.sourceReportId());
        if (existing.isPresent()) {
            return toDomain(existing.get());
        }
        ProfitDistributionJpaEntity saved = repository.save(ProfitDistributionJpaEntity.builder()
                .id(d.id())
                .companyId(d.companyId())
                .sourceReportId(d.sourceReportId())
                .creditBatchId(0L)
                .batchCodeSnap("REPORT-" + d.sourceReportId())
                .period("N/A")
                .unitPricePerCredit(BigDecimal.ZERO)
                .totalPayoutUsd(d.totalAmount())
                .status(d.status())
                .createdAt(d.createdAt())
                .build());
        return toDomain(saved);
    }

    private static ProfitDistribution toDomain(ProfitDistributionJpaEntity e) {
        return new ProfitDistribution(e.getId(), e.getCompanyId(), e.getSourceReportId(),
                "REPORT-" + e.getSourceReportId(), e.getTotalPayoutUsd(), 0, e.getStatus(), e.getCreatedAt());
    }
}
