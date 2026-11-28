package com.carbonx.ledger.api;

import com.carbonx.ledger.api.dto.LedgerDtos.IdResponse;
import com.carbonx.ledger.api.dto.LedgerDtos.ProfitShareRequest;
import com.carbonx.ledger.application.port.in.LedgerCommandUseCase;
import com.carbonx.ledger.application.port.in.LedgerQueryUseCase;
import com.carbonx.ledger.application.port.in.LedgerQueryUseCase.ProfitDistributionView;
import com.carbonx.ledger.application.security.AuthenticatedUser;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profit-sharing")
@RequiredArgsConstructor
@Tag(name = "Ledger — Profit Sharing")
public class ProfitSharingController {

    private final LedgerCommandUseCase ledgerCommandUseCase;
    private final LedgerQueryUseCase ledgerQueryUseCase;

    @GetMapping("/distributions")
    @PreAuthorize("hasAnyRole('COMPANY', 'ADMIN')")
    @Operation(summary = "List profit distributions")
    public List<ProfitDistributionView> listDistributions(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        return ledgerQueryUseCase.listDistributions(
                caller.effectiveCompanyId(), caller.hasRole("ROLE_ADMIN"));
    }

    @PostMapping("/share")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Execute EV owner profit distribution for an approved report")
    public IdResponse share(@AuthenticationPrincipal AuthenticatedUser principal,
                            @Valid @RequestBody ProfitShareRequest request) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        Long id = ledgerCommandUseCase.executeProfitDistribution(
                caller.effectiveCompanyId(), request.sourceReportId(), request.idempotencyKey());
        return new IdResponse(id);
    }
}
