package com.carbonx.ledger.infrastructure.persistence.repository;

import com.carbonx.ledger.infrastructure.persistence.entity.ProfitDistributionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfitDistributionJpaRepository extends JpaRepository<ProfitDistributionJpaEntity, Long> {
    Optional<ProfitDistributionJpaEntity> findByIdempotencyKey(String idempotencyKey);
    List<ProfitDistributionJpaEntity> findByCompanyIdOrderByIdDesc(Long companyId);
    List<ProfitDistributionJpaEntity> findAllByOrderByIdDesc();
}
