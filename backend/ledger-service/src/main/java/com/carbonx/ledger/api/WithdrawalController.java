package com.carbonx.ledger.api;

import com.carbonx.common.domain.Money;
import com.carbonx.ledger.api.dto.LedgerDtos.IdResponse;
import com.carbonx.ledger.application.port.in.LedgerCommandUseCase;
import com.carbonx.ledger.application.port.in.LedgerQueryUseCase;
import com.carbonx.ledger.application.port.in.LedgerQueryUseCase.WithdrawalView;
import com.carbonx.ledger.application.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/withdrawal")
@RequiredArgsConstructor
@Tag(name = "Ledger — Withdrawals")
public class WithdrawalController {

    private final LedgerCommandUseCase ledgerCommandUseCase;
    private final LedgerQueryUseCase ledgerQueryUseCase;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List current user's withdrawal requests")
    public List<WithdrawalView> myWithdrawals(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        return ledgerQueryUseCase.listMyWithdrawals(caller.userId());
    }

    @PostMapping("/{amount}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Request a withdrawal from current user's wallet")
    public IdResponse request(@AuthenticationPrincipal AuthenticatedUser principal,
                              @PathVariable BigDecimal amount) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        return new IdResponse(ledgerCommandUseCase.requestWithdrawal(caller.userId(), null, Money.of(amount)));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all withdrawal requests (ADMIN only)")
    public List<WithdrawalView> listAllForAdmin() {
        return ledgerQueryUseCase.listAllWithdrawals();
    }

    @PatchMapping("/admin/{id}/process/{accept}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve or reject a withdrawal request (ADMIN only)")
    public void process(@AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable Long id,
                        @PathVariable boolean accept) {
        AuthenticatedUser admin = PrincipalGuard.require(principal);
        ledgerCommandUseCase.processWithdrawal(admin.userId(), id, accept);
    }
}
