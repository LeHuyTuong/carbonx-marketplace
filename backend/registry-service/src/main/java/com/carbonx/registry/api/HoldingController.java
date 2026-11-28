package com.carbonx.registry.api;

import com.carbonx.registry.api.dto.RegistryDtos.HoldingResponse;
import com.carbonx.registry.api.dto.RegistryDtos.IdResponse;
import com.carbonx.registry.api.dto.RegistryDtos.MovementResponse;
import com.carbonx.registry.api.dto.RegistryDtos.RetireRequest;
import com.carbonx.registry.application.port.in.RegistryCommandUseCase;
import com.carbonx.registry.application.port.in.RegistryQueryUseCase;
import com.carbonx.registry.application.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Company-facing credit holdings: what the company owns, the serial ledger behind it, and the
 * retirement request that permanently cancels credits with a certificate.
 */
@RestController
@RequestMapping("/api/v1/holdings")
@RequiredArgsConstructor
@Tag(name = "Registry — Credit Holdings")
public class HoldingController {

    private final RegistryQueryUseCase registryQueryUseCase;
    private final RegistryCommandUseCase registryCommandUseCase;

    @GetMapping
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Every holding of the current company with its available/locked/retired serial ranges")
    public List<HoldingResponse> listMyHoldings(@AuthenticationPrincipal AuthenticatedUser principal) {
        return registryQueryUseCase.listCompanyHoldings(PrincipalGuard.require(principal).effectiveCompanyId()).stream()
                .map(HoldingResponse::from)
                .toList();
    }

    @GetMapping("/{holdingId}/movements")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Serial ledger of one holding (ISSUE, LOCK, RELEASE, TRANSFER_*, RETIRE)")
    public List<MovementResponse> listMovements(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable Long holdingId) {
        return registryQueryUseCase.listHoldingMovements(PrincipalGuard.require(principal).effectiveCompanyId(), holdingId)
                .stream()
                .map(MovementResponse::from)
                .toList();
    }

    @PostMapping("/{holdingId}/retire")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Permanently retire credits and issue a retirement certificate")
    public IdResponse retire(@AuthenticationPrincipal AuthenticatedUser principal,
                             @PathVariable Long holdingId,
                             @Valid @RequestBody RetireRequest request) {
        Long certificateId = registryCommandUseCase.retireCredits(
                PrincipalGuard.require(principal).effectiveCompanyId(),
                holdingId, request.quantity(), request.beneficiaryName(), request.reason());
        return new IdResponse(certificateId);
    }
}
