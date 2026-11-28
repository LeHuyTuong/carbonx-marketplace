package com.carbonx.identity.infrastructure.persistence.repository;

import com.carbonx.identity.infrastructure.persistence.entity.KycProfileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycProfileJpaRepository extends JpaRepository<KycProfileJpaEntity, Long> {
    Optional<KycProfileJpaEntity> findByEvOwnerId(Long evOwnerId);

    List<KycProfileJpaEntity> findAllByOrderByIdDesc();
}
