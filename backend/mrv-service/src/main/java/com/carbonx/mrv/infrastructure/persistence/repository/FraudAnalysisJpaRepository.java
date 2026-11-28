package com.carbonx.mrv.infrastructure.persistence.repository;

import com.carbonx.mrv.infrastructure.persistence.entity.FraudAnalysisJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FraudAnalysisJpaRepository extends JpaRepository<FraudAnalysisJpaEntity, Long> {

    List<FraudAnalysisJpaEntity> findByReportIdOrderByIdAsc(Long reportId);

    void deleteByReportId(Long reportId);
}
