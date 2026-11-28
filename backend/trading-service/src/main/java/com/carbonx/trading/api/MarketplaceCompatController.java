package com.carbonx.trading.api;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.api.dto.TradingDtos.IdResponse;
import com.carbonx.trading.api.dto.TradingDtos.ListingResponse;
import com.carbonx.trading.api.dto.TradingDtos.OrderExecutionResponse;
import com.carbonx.trading.api.dto.TradingDtos.OrderResponse;
import com.carbonx.trading.application.exception.TradingApplicationException;
import com.carbonx.trading.application.port.in.TradingCommandUseCase;
import com.carbonx.trading.application.port.in.TradingQueryUseCase;
import com.carbonx.trading.application.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Frontend-compatible marketplace and order facade backed by {@link TradingCommandUseCase}
 * and {@link TradingQueryUseCase}, keeping `/api/v1/trading/**` as canonical.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Trading — Compatibility Facade")
public class MarketplaceCompatController {

    private final TradingQueryUseCase tradingQueryUseCase;
    private final TradingCommandUseCase tradingCommandUseCase;

    @GetMapping("/marketplace")
    @Operation(summary = "Browse active marketplace listings (public compat route)")
    public List<ListingResponse> listMarketplace(@RequestParam(required = false) Long batchId,
                                                 @RequestParam(required = false) String sort) {
        return tradingQueryUseCase.listActiveMarketplace(batchId, sort).stream()
                .map(ListingResponse::from)
                .toList();
    }

    @GetMapping("/marketplace/company")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "List active and historical listings created by the current company")
    public List<ListingResponse> listCompanyListings(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        return tradingQueryUseCase.listMyListings(caller.effectiveCompanyId()).stream()
                .map(ListingResponse::from)
                .toList();
    }

    @PostMapping("/marketplace")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Create a marketplace listing from the caller's credit holding")
    public IdResponse createCompatListing(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @Valid @RequestBody CompatListingRequest request) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        Long targetHoldingId = request.resolvedHoldingId();
        Long quantity = request.resolvedQuantity();
        BigDecimal price = request.resolvedPricePerCredit();
        if (targetHoldingId == null || quantity == null || quantity <= 0 || price == null || price.signum() <= 0) {
            throw TradingApplicationException.badRequest(
                    "holdingId, positive quantity, and positive pricePerCredit are required");
        }
        Long listingId = tradingCommandUseCase.createListing(caller.effectiveCompanyId(), caller.userId(),
                targetHoldingId, quantity, Money.of(price), request.resolvedExpiresAt());
        return new IdResponse(listingId);
    }

    @DeleteMapping("/marketplace/{listingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Cancel a company's active marketplace listing")
    public void deleteCompatListing(@AuthenticationPrincipal AuthenticatedUser principal,
                                    @PathVariable Long listingId) {
        tradingCommandUseCase.cancelListing(PrincipalGuard.require(principal).effectiveCompanyId(), listingId);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "List orders placed by the current company (compat route)")
    public List<OrderResponse> listCompatOrders(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        return tradingQueryUseCase.listMyOrders(caller.effectiveCompanyId()).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Place and settle an instant-buy order via the Order Saga (compat route)")
    public OrderExecutionResponse createCompatOrder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CompatOrderRequest request) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        Long listingId = request.resolvedListingId();
        Long quantity = request.resolvedQuantity();
        if (listingId == null || quantity == null || quantity <= 0) {
            throw TradingApplicationException.badRequest("listingId and positive quantity are required");
        }
        String key = (idempotencyKey == null || idempotencyKey.isBlank())
                ? "compat-" + UUID.randomUUID()
                : idempotencyKey;
        Long orderId = tradingCommandUseCase.executeInstantBuyOrderSaga(
                key, caller.effectiveCompanyId(), caller.userId(), listingId, quantity);
        return new OrderExecutionResponse(orderId, "COMPLETED");
    }

    public record CompatListingPayload(Long holdingId, Long batchId, Long quantity, BigDecimal pricePerCredit,
                                       Instant expiresAt) {
    }

    public record CompatListingRequest(Long holdingId, Long batchId, Long quantity, BigDecimal pricePerCredit,
                                       Instant expiresAt, CompatListingPayload data) {
        public Long resolvedHoldingId() {
            if (holdingId != null) {
                return holdingId;
            }
            if (data != null && data.holdingId() != null) {
                return data.holdingId();
            }
            return batchId != null ? batchId : (data != null ? data.batchId() : null);
        }

        public Long resolvedQuantity() {
            return quantity != null ? quantity : (data != null ? data.quantity() : null);
        }

        public BigDecimal resolvedPricePerCredit() {
            return pricePerCredit != null ? pricePerCredit : (data != null ? data.pricePerCredit() : null);
        }

        public Instant resolvedExpiresAt() {
            return expiresAt != null ? expiresAt : (data != null ? data.expiresAt() : null);
        }
    }

    public record CompatOrderPayload(Long listingId, Long quantity) {
    }

    public record CompatOrderRequest(Long listingId, Long quantity, CompatOrderPayload data) {
        public Long resolvedListingId() {
            return listingId != null ? listingId : (data != null ? data.listingId() : null);
        }

        public Long resolvedQuantity() {
            return quantity != null ? quantity : (data != null ? data.quantity() : null);
        }
    }
}
