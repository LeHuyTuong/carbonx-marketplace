package com.carbonx.registry.infrastructure.persistence.repository;

import com.carbonx.registry.infrastructure.persistence.entity.RetirementCertificateJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetirementCertificateJpaRepository extends JpaRepository<RetirementCertificateJpaEntity, Long> {
}
