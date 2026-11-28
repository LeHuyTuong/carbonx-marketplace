package com.carbonx.trading.infrastructure.persistence.adapter;

import com.carbonx.trading.application.port.out.TradeOrderRepositoryPort;
import com.carbonx.trading.domain.order.TradeOrder;
import com.carbonx.trading.infrastructure.persistence.entity.TradeOrderJpaEntity;
import com.carbonx.trading.infrastructure.persistence.repository.TradeOrderJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adapts the TradeOrder aggregate to {@code trade_orders}. */
@Component
@RequiredArgsConstructor
public class TradeOrderPersistenceAdapter implements TradeOrderRepositoryPort {

    private final TradeOrderJpaRepository repository;

    @Override
    public TradeOrder save(TradeOrder order) {
        TradeOrderJpaEntity entity;
        if (order.id() == null) {
            entity = TradeOrderJpaEntity.empty();
        } else {
            entity = repository.findById(order.id())
                    .orElseThrow(() -> new IllegalStateException("Order " + order.id() + " disappeared"));
        }
        entity.apply(order);
        return repository.save(entity).toDomain();
    }

    @Override
    public Optional<TradeOrder> findById(Long id) {
        return repository.findById(id).map(TradeOrderJpaEntity::toDomain);
    }

    @Override
    public Optional<TradeOrder> findByOrderCode(String orderCode) {
        return repository.findByOrderCode(orderCode).map(TradeOrderJpaEntity::toDomain);
    }

    @Override
    public List<TradeOrder> findByBuyerCompanyId(Long buyerCompanyId) {
        return repository.findByBuyerCompanyIdOrderByIdDesc(buyerCompanyId).stream()
                .map(TradeOrderJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<TradeOrder> findByListingId(Long listingId) {
        return repository.findByListingIdOrderByIdAsc(listingId).stream()
                .map(TradeOrderJpaEntity::toDomain)
                .toList();
    }
}
