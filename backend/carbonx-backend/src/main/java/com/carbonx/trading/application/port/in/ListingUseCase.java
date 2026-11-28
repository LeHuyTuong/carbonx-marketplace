package com.carbonx.trading.application.port.in;

import com.carbonx.shared.domain.Money;

import java.time.Instant;
import java.util.List;

/** Inbound Use-Case Port: Marketplace Listings (lock/release CreditHoldings via RegistryCommandPort). */
public interface ListingUseCase {
    ListingView createListing(Long sellerCompanyId, CreateListingCommand command);

    void cancelListing(Long sellerCompanyId, Long listingId);

    List<ListingView> listPublicMarketplace(Long projectIdFilter, String sortBy);

    List<ListingView> listMyListings(Long sellerCompanyId);

    record CreateListingCommand(Long holdingId, long quantity, Money pricePerCredit, Instant expiresAt) {}

    record ListingView(
            Long id,
            Long holdingId,
            Long batchId,
            String batchCode,
            String projectTitle,
            Long sellerCompanyId,
            long totalQty,
            long availableQty,
            Money pricePerCredit,
            String status,
            Instant expiresAt
    ) {}
}
