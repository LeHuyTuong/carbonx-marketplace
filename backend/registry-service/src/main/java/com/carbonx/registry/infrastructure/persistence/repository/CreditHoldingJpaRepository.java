package com.carbonx.registry.infrastructure.persistence.repository;

import com.carbonx.registry.infrastructure.persistence.entity.CreditHoldingJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CreditHoldingJpaRepository extends JpaRepository<CreditHoldingJpaEntity, Long> {

    List<CreditHoldingJpaEntity> findByHolderCompanyIdOrderByIdAsc(Long holderCompanyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from CreditHoldingJpaEntity h where h.id = :id")
    Optional<CreditHoldingJpaEntity> lockById(@Param("id") Long id);
}
