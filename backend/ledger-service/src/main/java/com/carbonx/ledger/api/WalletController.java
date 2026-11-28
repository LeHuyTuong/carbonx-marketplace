package com.carbonx.ledger.api;

import com.carbonx.common.domain.Money;
import com.carbonx.ledger.api.dto.LedgerDtos.DepositRequest;
import com.carbonx.ledger.application.port.in.LedgerCommandUseCase;
import com.carbonx.ledger.application.port.in.LedgerQueryUseCase;
import com.carbonx.ledger.application.port.in.LedgerQueryUseCase.WalletTransactionView;
import com.carbonx.ledger.application.port.in.LedgerQueryUseCase.WalletView;
import com.carbonx.ledger.application.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Tag(name = "Ledger — Wallet")
public class WalletController {

    private final LedgerCommandUseCase ledgerCommandUseCase;
    private final LedgerQueryUseCase ledgerQueryUseCase;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current user's wallet balance (auto-provisions if missing)")
    public WalletView myWallet(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        return ledgerQueryUseCase.getOrCreateWallet(caller.userId());
    }

    @PostMapping("/deposit")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Deposit USD into current user's wallet")
    public Map<String, String> deposit(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @Valid @RequestBody DepositRequest request) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        String reference = ledgerCommandUseCase.initiateDeposit(
                caller.userId(), Money.of(request.amount()), request.provider());
        return Map.of("reference", reference);
    }

    @GetMapping("/transactions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List wallet transactions (ADMIN sees all, users see their own)")
    public List<WalletTransactionView> listTransactions(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        return ledgerQueryUseCase.listTransactions(caller.userId(), caller.hasRole("ROLE_ADMIN"));
    }

    @GetMapping("/transactions/count")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Total count of wallet transactions (ADMIN only)")
    public Map<String, Long> countTransactions() {
        return Map.of("count", ledgerQueryUseCase.countAllTransactions());
    }
}
