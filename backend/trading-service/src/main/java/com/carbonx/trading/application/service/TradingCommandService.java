package com.carbonx.trading.application.service;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.application.exception.TradingApplicationException;
import com.carbonx.trading.application.port.in.TradingCommandUseCase;
import com.carbonx.trading.application.port.out.CreditHoldingProjectionPort;
import com.carbonx.trading.application.port.out.ListingReservationRepositoryPort;
import com.carbonx.trading.application.port.out.MarketListingRepositoryPort;
import com.carbonx.trading.application.port.out.OrderCreditLockPort;
import com.carbonx.trading.application.port.out.PlatformSettingRepositoryPort;
import com.carbonx.trading.domain.order.ListingStatus;
import com.carbonx.trading.domain.order.MarketListing;
import com.carbonx.trading.domain.order.ReservationStatus;
import com.carbonx.trading.domain.setting.TradingFeeSetting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Command side of the trading CQRS split: listing lifecycle plus the entry point of the Order
 * Saga. The saga entry point is deliberately NOT transactional — each step owns its own short
 * transaction, and no database lock is held across a remote call.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradingCommandService implements TradingCommandUseCase {

    private final MarketListingRepositoryPort listingRepository;
    private final ListingReservationRepositoryPort reservationRepository;
    private final CreditHoldingProjectionPort holdingProjection;
    private final OrderCreditLockPort creditLock;
    private final OrderSagaService orderSagaService;
    private final PlatformSettingRepositoryPort platformSettingRepository;

    @Override
    public Long createListing(Long sellerCompanyId, Long holdingId, long quantity, Money pricePerCredit,
                              Instant expiresAt) {
        return createListing(sellerCompanyId, sellerCompanyId, holdingId, quantity, pricePerCredit, expiresAt);
    }

    @Override
    public Long createListing(Long sellerCompanyId, Long sellerUserId, Long holdingId, long quantity,
                              Money pricePerCredit, Instant expiresAt) {
        if (sellerCompanyId == null) {
            throw TradingApplicationException.forbidden("Authentication is required");
        }
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw TradingApplicationException.badRequest("expiresAt must be in the future");
        }
        CreditHoldingProjectionPort.HoldingSnapshot holding = holdingProjection.fetchOwnedHolding(holdingId);
        if (quantity > holding.availableQty()) {
            throw TradingApplicationException.listingUnavailable("Holding " + holdingId + " has only "
                    + holding.availableQty() + " available credits, cannot list " + quantity);
        }
        MarketListing listing = MarketListing.list(holding.holdingId(), holding.batchId(), holding.batchCode(),
                holding.projectTitle(), sellerCompanyId, sellerUserId, quantity, pricePerCredit, expiresAt);
        String lockKey = "listing-lock:" + UUID.randomUUID();
        creditLock.lockCreditsForListing(lockKey, holding.holdingId(), sellerCompanyId, quantity,
                "holding-" + holding.holdingId());
        try {
            Long listingId = listingRepository.save(listing).id();
            log.info("Company {} (user {}) listed {} credits of batch {} at {} (listing {})",
                    sellerCompanyId, sellerUserId, quantity, holding.batchCode(), pricePerCredit.amount(), listingId);
            return listingId;
        } catch (RuntimeException ex) {
            rollbackHoldingLock(lockKey, holding.holdingId(), quantity);
            throw ex;
        }
    }

    @Override
    @Transactional
    public void cancelListing(Long sellerCompanyId, Long listingId) {
        MarketListing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> TradingApplicationException.notFound("Listing not found: " + listingId));
        if (!listing.isOwnedBy(sellerCompanyId)) {
            throw TradingApplicationException.forbidden("Listing " + listingId + " belongs to another company");
        }
        boolean reserved = !reservationRepository
                .findByListingIdAndStatus(listingId, ReservationStatus.RESERVED).isEmpty();
        if (reserved) {
            throw TradingApplicationException.conflict(
                    "Listing " + listingId + " has reservations in flight; retry after the sagas settle");
        }
        if (listing.status() == ListingStatus.CANCELLED) {
            return;
        }
        listingRepository.save(listing.cancel());
        if (listing.availableQty() > 0) {
            creditLock.releaseLockedCredits("listing-cancel:" + listingId, listing.holdingId(),
                    listing.availableQty(), "Listing " + listingId + " cancelled");
        }
        log.info("Company {} cancelled listing {} (released {} credits)",
                sellerCompanyId, listingId, listing.availableQty());
    }

    private void rollbackHoldingLock(String lockKey, Long holdingId, long quantity) {
        try {
            creditLock.releaseLockedCredits(lockKey + ":rollback", holdingId, quantity,
                    "Listing persistence failed rollback");
        } catch (RuntimeException rollbackFailure) {
            log.error("Failed to release locked credits on holding {} after listing save failure (key={}): {}",
                    holdingId, lockKey, rollbackFailure.getMessage());
        }
    }

    @Override
    public Long executeInstantBuyOrderSaga(String idempotencyKey, Long buyerCompanyId, Long buyerUserId,
                                           Long listingId, long quantity) {
        return orderSagaService.executeInstantBuy(idempotencyKey, buyerCompanyId, buyerUserId, listingId, quantity);
    }

    @Override
    @Transactional
    public TradingFeeSetting updateTradingFeeRate(Long adminUserId, BigDecimal feeRate) {
        if (adminUserId == null) {
            throw TradingApplicationException.forbidden("Authentication is required");
        }
        TradingFeeSetting saved = platformSettingRepository.saveTradingFeeRate(adminUserId, feeRate);
        log.info("Admin {} updated trading fee rate to {}", adminUserId, saved.feeRate());
        return saved;
    }
}
