package com.carbonx.trading.infrastructure.persistence.repository;

import com.carbonx.trading.infrastructure.persistence.entity.MarketListingJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MarketListingJpaRepository extends JpaRepository<MarketListingJpaEntity, Long> {

    List<MarketListingJpaEntity> findByStatus(String status, Sort sort);

    List<MarketListingJpaEntity> findByStatusAndBatchId(String status, Long batchId, Sort sort);

    List<MarketListingJpaEntity> findBySellerCompanyIdOrderByIdDesc(Long sellerCompanyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from MarketListingJpaEntity l where l.id = :id")
    Optional<MarketListingJpaEntity> lockById(@Param("id") Long id);
}
