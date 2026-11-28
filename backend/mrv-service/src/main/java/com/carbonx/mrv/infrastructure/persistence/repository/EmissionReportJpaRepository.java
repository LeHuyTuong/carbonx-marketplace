package com.carbonx.mrv.infrastructure.persistence.repository;

import com.carbonx.mrv.infrastructure.persistence.entity.EmissionReportJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmissionReportJpaRepository extends JpaRepository<EmissionReportJpaEntity, Long> {

    List<EmissionReportJpaEntity> findByCompanyIdOrderBySubmittedAtDesc(Long companyId);

    List<EmissionReportJpaEntity> findByStatusOrderBySubmittedAtAsc(String status);

    List<EmissionReportJpaEntity> findAllByOrderBySubmittedAtDesc();
}
