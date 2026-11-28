package com.carbonx.trading.api;

import com.carbonx.common.domain.Money;
import com.carbonx.trading.api.dto.TradingDtos.CreateListingRequest;
import com.carbonx.trading.api.dto.TradingDtos.IdResponse;
import com.carbonx.trading.api.dto.TradingDtos.ListingResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Marketplace: browse active listings, publish one from a holding, withdraw one. */
@RestController
@RequestMapping("/api/v1/trading/listings")
@RequiredArgsConstructor
@Tag(name = "Trading — Marketplace Listings")
public class ListingController {

    private final TradingQueryUseCase tradingQueryUseCase;
    private final TradingCommandUseCase tradingCommandUseCase;

    @GetMapping
    @Operation(summary = "Active listings, sorted by price (price_asc | price_desc | newest) — public guest read")
    public List<ListingResponse> listActive(@RequestParam(required = false) Long batchId,
                                            @RequestParam(required = false) String sort) {
        return tradingQueryUseCase.listActiveMarketplace(batchId, sort).stream()
                .map(ListingResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "List credits of one of the caller's holdings on the marketplace")
    public IdResponse create(@AuthenticationPrincipal AuthenticatedUser principal,
                             @Valid @RequestBody CreateListingRequest request) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        Long listingId = tradingCommandUseCase.createListing(caller.effectiveCompanyId(), caller.userId(),
                request.holdingId(), request.quantity(), Money.of(request.pricePerCredit()),
                request.expiresAt());
        return new IdResponse(listingId);
    }

    @DeleteMapping("/{listingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Withdraw a listing that has no saga in flight")
    public void cancel(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long listingId) {
        tradingCommandUseCase.cancelListing(PrincipalGuard.require(principal).effectiveCompanyId(), listingId);
    }
}
