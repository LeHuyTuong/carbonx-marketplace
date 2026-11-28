package com.carbonx.trading.application.port.out;

import com.carbonx.trading.domain.order.MarketListing;

import java.util.List;
import java.util.Optional;

/** Persistence contract of {@code market_listings}. */
public interface MarketListingRepositoryPort {

    MarketListing save(MarketListing listing);

    Optional<MarketListing> findById(Long id);

    /** {@code SELECT ... FOR UPDATE}: two sagas can never reserve the same quantity. */
    Optional<MarketListing> findByIdForUpdate(Long id);

    /**
     * Active (LISTED) listings. The V1 projection carries the batch, not the project, so the
     * {@code batchIdFilter} narrows the marketplace to the batch a project issued.
     */
    List<MarketListing> findActive(Long batchIdFilter, String sortBy);

    List<MarketListing> findBySellerCompanyId(Long sellerCompanyId);
}
