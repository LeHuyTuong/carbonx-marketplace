package com.carbonx.trading.api;

import com.carbonx.trading.application.exception.TradingApplicationException;
import com.carbonx.trading.application.port.in.TradingCommandUseCase;
import com.carbonx.trading.application.port.in.TradingQueryUseCase;
import com.carbonx.trading.application.security.AuthenticatedUser;
import com.carbonx.trading.domain.setting.TradingFeeSetting;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Exposes the public read-only platform transaction fee rate and the ADMIN-only endpoint to
 * update it at runtime without redeploying services.
 */
@RestController
@RequestMapping("/api/v1/trading/settings")
@RequiredArgsConstructor
@Tag(name = "Trading — Platform Settings")
public class PlatformSettingsController {

    private final TradingCommandUseCase tradingCommandUseCase;
    private final TradingQueryUseCase tradingQueryUseCase;

    @GetMapping("/fee")
    @Operation(summary = "Read the active platform transaction fee rate (public)")
    public TradingFeeResponse getFeeSetting() {
        return TradingFeeResponse.from(tradingQueryUseCase.getTradingFeeSetting());
    }

    @PutMapping("/fee")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update the platform transaction fee rate (ADMIN only)")
    public TradingFeeResponse updateFeeSetting(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateTradingFeeRequest request) {
        AuthenticatedUser admin = PrincipalGuard.require(principal);
        BigDecimal nextRate;
        if (request.feeRate() != null) {
            nextRate = request.feeRate();
        } else if (request.feePercent() != null) {
            nextRate = TradingFeeSetting.parsePercentToRate(request.feePercent());
        } else {
            throw TradingApplicationException.badRequest("feeRate or feePercent is required");
        }
        return TradingFeeResponse.from(tradingCommandUseCase.updateTradingFeeRate(admin.userId(), nextRate));
    }

    public record UpdateTradingFeeRequest(BigDecimal feeRate, BigDecimal feePercent) {
    }

    public record TradingFeeResponse(BigDecimal feeRate, BigDecimal feePercent, Long updatedByUserId,
                                     Instant updatedAt) {
        public static TradingFeeResponse from(TradingFeeSetting setting) {
            return new TradingFeeResponse(setting.feeRate(), setting.feePercent(),
                    setting.updatedByUserId(), setting.updatedAt());
        }
    }
}
