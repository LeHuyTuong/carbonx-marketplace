package com.carbonx.ledger.infrastructure.persistence.repository;

import com.carbonx.ledger.infrastructure.persistence.entity.WalletJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletJpaRepository extends JpaRepository<WalletJpaEntity, Long> {

    Optional<WalletJpaEntity> findByOwnerUserId(Long ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletJpaEntity w WHERE w.ownerUserId = :ownerUserId")
    Optional<WalletJpaEntity> findByOwnerUserIdForUpdate(@Param("ownerUserId") Long ownerUserId);
}
