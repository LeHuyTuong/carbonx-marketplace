package com.carbonx.trading.infrastructure.persistence.repository;

import com.carbonx.trading.infrastructure.persistence.entity.TradeOrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeOrderJpaRepository extends JpaRepository<TradeOrderJpaEntity, Long> {

    Optional<TradeOrderJpaEntity> findByOrderCode(String orderCode);

    Optional<TradeOrderJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<TradeOrderJpaEntity> findByBuyerCompanyIdOrderByIdDesc(Long buyerCompanyId);

    List<TradeOrderJpaEntity> findByListingIdOrderByIdAsc(Long listingId);
}
