package com.carbonx.identity.infrastructure.persistence.repository;

import com.carbonx.identity.infrastructure.persistence.entity.CompanyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyJpaRepository extends JpaRepository<CompanyJpaEntity, Long> {
    Optional<CompanyJpaEntity> findByUserId(Long userId);

    List<CompanyJpaEntity> findByKycStatusOrderByBusinessNameAsc(String kycStatus);

    List<CompanyJpaEntity> findAllByOrderByIdDesc();
}
