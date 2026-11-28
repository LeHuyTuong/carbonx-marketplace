package com.carbonx.trading.api;

import com.carbonx.trading.api.dto.TradingDtos.ExecuteOrderRequest;
import com.carbonx.trading.api.dto.TradingDtos.OrderExecutionResponse;
import com.carbonx.trading.api.dto.TradingDtos.OrderResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * One-step instant purchase. The {@code Idempotency-Key} header is mandatory: replaying the same
 * key returns the already created order instead of buying twice.
 */
@RestController
@RequestMapping("/api/v1/trading/orders")
@RequiredArgsConstructor
@Tag(name = "Trading — Orders")
public class OrderController {

    private final TradingCommandUseCase tradingCommandUseCase;
    private final TradingQueryUseCase tradingQueryUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Buy credits now (Order Saga: reserve -> pay -> transfer -> finalize)")
    public OrderExecutionResponse placeOrder(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             @Valid @RequestBody ExecuteOrderRequest request) {
        AuthenticatedUser buyer = PrincipalGuard.require(principal);
        Long orderId = tradingCommandUseCase.executeInstantBuyOrderSaga(idempotencyKey, buyer.effectiveCompanyId(),
                buyer.userId(), request.listingId(), request.quantity());
        return new OrderExecutionResponse(orderId, "COMPLETED");
    }

    @GetMapping
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Orders bought by the current company")
    public List<OrderResponse> myOrders(@AuthenticationPrincipal AuthenticatedUser principal) {
        return tradingQueryUseCase.listMyOrders(PrincipalGuard.require(principal).effectiveCompanyId()).stream()
                .map(OrderResponse::from)
                .toList();
    }
}
