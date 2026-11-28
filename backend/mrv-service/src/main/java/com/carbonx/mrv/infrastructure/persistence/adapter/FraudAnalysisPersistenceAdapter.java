package com.carbonx.mrv.infrastructure.persistence.adapter;

import com.carbonx.mrv.application.port.out.FraudAnalysisRepositoryPort;
import com.carbonx.mrv.domain.fraud.FraudFinding;
import com.carbonx.mrv.infrastructure.persistence.entity.FraudAnalysisJpaEntity;
import com.carbonx.mrv.infrastructure.persistence.repository.FraudAnalysisJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Adapts the DQ1..DQ8 findings to the {@code fraud_analyses} table. */
@Component
@RequiredArgsConstructor
public class FraudAnalysisPersistenceAdapter implements FraudAnalysisRepositoryPort {

    private final FraudAnalysisJpaRepository repository;

    @Override
    @Transactional
    public void replaceForReport(Long reportId, List<FraudFinding> findings) {
        repository.deleteByReportId(reportId);
        if (findings == null || findings.isEmpty()) {
            return;
        }
        repository.saveAll(findings.stream()
                .map(finding -> FraudAnalysisJpaEntity.of(reportId, finding))
                .toList());
    }

    @Override
    public List<FraudFinding> findByReportId(Long reportId) {
        return repository.findByReportIdOrderByIdAsc(reportId).stream()
                .map(FraudAnalysisJpaEntity::toDomain)
                .toList();
    }
}
