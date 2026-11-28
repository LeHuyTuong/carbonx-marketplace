package com.carbonx.trading.api.dto;

import com.carbonx.trading.application.port.in.TradingQueryUseCase.ListingView;
import com.carbonx.trading.application.port.in.TradingQueryUseCase.OrderView;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/** Marketplace listing and instant-buy order contracts. */
public final class TradingDtos {

    private TradingDtos() {
    }

    public record CreateListingRequest(
            @NotNull @Positive Long holdingId,
            @Positive long quantity,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal pricePerCredit,
            Instant expiresAt) {
    }

    public record ExecuteOrderRequest(
            @NotNull @Positive Long listingId,
            @Positive long quantity) {
    }

    public record IdResponse(Long id) {
    }

    public record OrderExecutionResponse(Long orderId, String status) {
    }

    public record ListingResponse(Long id, Long batchId, String batchCode, String projectTitle,
                                  Long sellerCompanyId, long availableQty, BigDecimal pricePerCredit,
                                  String status) {
        public static ListingResponse from(ListingView view) {
            return new ListingResponse(view.id(), view.batchId(), view.batchCode(), view.projectTitle(),
                    view.sellerCompanyId(), view.availableQty(), view.pricePerCredit().amount(), view.status());
        }
    }

    public record OrderResponse(Long id, String orderCode, Long batchId, long quantity, BigDecimal grossAmount,
                                BigDecimal platformFee, String status, Instant settledAt) {
        public static OrderResponse from(OrderView view) {
            return new OrderResponse(view.id(), view.orderCode(), view.batchId(), view.quantity(),
                    view.grossAmount().amount(), view.platformFee().amount(), view.status(), view.settledAt());
        }
    }
}
