package com.carbonx.ledger.api;

import com.carbonx.ledger.api.dto.LedgerDtos.PaymentDetailsInput;
import com.carbonx.ledger.application.port.in.LedgerCommandUseCase;
import com.carbonx.ledger.application.port.in.LedgerQueryUseCase;
import com.carbonx.ledger.application.port.in.LedgerQueryUseCase.PaymentDetailsView;
import com.carbonx.ledger.application.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/paymentDetails")
@RequiredArgsConstructor
@Tag(name = "Ledger — Payment Details")
public class PaymentDetailsController {

    private final LedgerCommandUseCase ledgerCommandUseCase;
    private final LedgerQueryUseCase ledgerQueryUseCase;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current user's bank account details")
    public PaymentDetailsView get(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        return ledgerQueryUseCase.getPaymentDetails(caller.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Save or update current user's bank account details")
    public void save(@AuthenticationPrincipal AuthenticatedUser principal,
                     @RequestBody PaymentDetailsInput request) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        PaymentDetailsInput input = request.resolved();
        ledgerCommandUseCase.savePaymentDetails(caller.userId(), input.bankName(),
                input.accountNumber(), input.accountHolder(), input.branch());
    }
}
