package com.carbonx.ledger.infrastructure.persistence.repository;

import com.carbonx.ledger.infrastructure.persistence.entity.WalletJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletJpaRepository extends JpaRepository<WalletJpaEntity, Long> {

    Optional<WalletJpaEntity> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletJpaEntity w WHERE w.userId = :userId")
    Optional<WalletJpaEntity> findByUserIdForUpdate(@Param("userId") Long userId);
}
