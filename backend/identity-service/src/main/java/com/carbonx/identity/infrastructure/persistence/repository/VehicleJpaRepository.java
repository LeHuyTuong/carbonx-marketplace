package com.carbonx.identity.infrastructure.persistence.repository;

import com.carbonx.identity.infrastructure.persistence.entity.VehicleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface VehicleJpaRepository extends JpaRepository<VehicleJpaEntity, Long> {
    List<VehicleJpaEntity> findByEvOwnerIdOrderByCreatedAtDesc(Long evOwnerId);

    List<VehicleJpaEntity> findByCompanyIdAndPlateNumberIn(Long companyId, Collection<String> plateNumbers);

    List<VehicleJpaEntity> findByCompanyIdOrderByIdDesc(Long companyId);

    boolean existsByPlateNumber(String plateNumber);
}
