package com.carbonx.ledger.api;

import com.carbonx.common.domain.Money;
import com.carbonx.ledger.api.dto.LedgerDtos.RefundPaymentRequest;
import com.carbonx.ledger.api.dto.LedgerDtos.SettlePaymentRequest;
import com.carbonx.ledger.application.port.in.LedgerCommandUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** Service-to-service endpoints used by trading-service Order Saga. */
@RestController
@RequestMapping("/api/v1/internal/wallets")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
@RequiredArgsConstructor
@Tag(name = "Ledger — Internal (Order Saga)")
public class InternalWalletController {

    private final LedgerCommandUseCase ledgerCommandUseCase;

    @PostMapping("/orders/{orderRef}/settle")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    @Operation(summary = "Debit buyer and credit seller (minus platform fee) idempotently")
    public void settleOrderPayment(@PathVariable Long orderRef,
                                   @Valid @RequestBody SettlePaymentRequest request) {
        BigDecimal fee = request.platformFee() == null ? BigDecimal.ZERO : request.platformFee();
        ledgerCommandUseCase.settleOrderPayment(request.idempotencyKey(), orderRef,
                request.buyerUserId(), request.sellerUserId(),
                Money.of(request.grossAmount()), Money.of(fee));
    }

    @PostMapping("/orders/{orderRef}/refund")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    @Operation(summary = "Compensation step: refund buyer wallet idempotently")
    public void refundOrderPayment(@PathVariable Long orderRef,
                                   @Valid @RequestBody RefundPaymentRequest request) {
        ledgerCommandUseCase.compensateOrderPayment(request.idempotencyKey(), orderRef, request.reason());
    }
}
