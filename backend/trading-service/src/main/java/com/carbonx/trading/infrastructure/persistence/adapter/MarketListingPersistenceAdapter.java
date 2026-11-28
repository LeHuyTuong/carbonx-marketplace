package com.carbonx.trading.infrastructure.persistence.adapter;

import com.carbonx.trading.application.port.out.MarketListingRepositoryPort;
import com.carbonx.trading.domain.order.ListingStatus;
import com.carbonx.trading.domain.order.MarketListing;
import com.carbonx.trading.infrastructure.persistence.entity.MarketListingJpaEntity;
import com.carbonx.trading.infrastructure.persistence.repository.MarketListingJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Adapts the MarketListing aggregate to {@code market_listings}. */
@Component
@RequiredArgsConstructor
public class MarketListingPersistenceAdapter implements MarketListingRepositoryPort {

    private final MarketListingJpaRepository repository;

    @Override
    public MarketListing save(MarketListing listing) {
        MarketListingJpaEntity entity;
        if (listing.id() == null) {
            entity = MarketListingJpaEntity.empty();
        } else {
            entity = repository.findById(listing.id())
                    .orElseThrow(() -> new IllegalStateException("Listing " + listing.id() + " disappeared"));
        }
        entity.apply(listing);
        return repository.save(entity).toDomain();
    }

    @Override
    public Optional<MarketListing> findById(Long id) {
        return repository.findById(id).map(MarketListingJpaEntity::toDomain);
    }

    @Override
    public Optional<MarketListing> findByIdForUpdate(Long id) {
        return repository.lockById(id).map(MarketListingJpaEntity::toDomain);
    }

    @Override
    public List<MarketListing> findActive(Long batchIdFilter, String sortBy) {
        String listed = ListingStatus.LISTED.name();
        Sort sort = sortOf(sortBy);
        List<MarketListingJpaEntity> rows = batchIdFilter == null
                ? repository.findByStatus(listed, sort)
                : repository.findByStatusAndBatchId(listed, batchIdFilter, sort);
        return rows.stream().map(MarketListingJpaEntity::toDomain).toList();
    }

    @Override
    public List<MarketListing> findBySellerCompanyId(Long sellerCompanyId) {
        return repository.findBySellerCompanyIdOrderByIdDesc(sellerCompanyId).stream()
                .map(MarketListingJpaEntity::toDomain)
                .toList();
    }

    private static Sort sortOf(String sortBy) {
        return switch (sortBy == null ? "" : sortBy.trim().toLowerCase(Locale.ROOT)) {
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "pricePerCredit");
            case "newest" -> Sort.by(Sort.Direction.DESC, "id");
            default -> Sort.by(Sort.Direction.ASC, "pricePerCredit");
        };
    }
}
