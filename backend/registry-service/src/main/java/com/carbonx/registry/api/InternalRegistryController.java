package com.carbonx.registry.api;

import com.carbonx.registry.api.dto.RegistryDtos.LockRequest;
import com.carbonx.registry.api.dto.RegistryDtos.LockResponse;
import com.carbonx.registry.api.dto.RegistryDtos.ReleaseRequest;
import com.carbonx.registry.api.dto.RegistryDtos.TransferRequest;
import com.carbonx.registry.api.dto.RegistryDtos.TransferResponse;
import com.carbonx.registry.application.port.in.RegistryCommandUseCase;
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

/**
 * Service-to-service endpoints used by the trading Order Saga. Guarded strictly by
 * {@code X-Internal-Service-Token} ({@code ROLE_INTERNAL_SERVICE}) so end-user JWTs cannot
 * bypass ledger settlement by calling registry directly.
 */
@RestController
@RequestMapping("/api/v1/internal/holdings")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
@RequiredArgsConstructor
@Tag(name = "Registry — Internal (Order Saga)")
public class InternalRegistryController {

    private final RegistryCommandUseCase registryCommandUseCase;

    @PostMapping("/{holdingId}/lock")
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    @Operation(summary = "Lock credits for one listing (idempotent on idempotencyKey)")
    public LockResponse lock(@PathVariable Long holdingId, @Valid @RequestBody LockRequest request) {
        return LockResponse.from(registryCommandUseCase.lockHoldingCredits(request.idempotencyKey(), holdingId,
                request.sellerCompanyId(), request.quantity(), request.listingRef()));
    }

    @PostMapping("/{holdingId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    @Operation(summary = "Compensation step: release a reservation back to the seller holding")
    public void release(@PathVariable Long holdingId, @Valid @RequestBody ReleaseRequest request) {
        registryCommandUseCase.compensateReleaseLockedCredits(request.idempotencyKey(), holdingId,
                request.quantity(), request.reason());
    }

    @PostMapping("/{holdingId}/transfer")
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    @Operation(summary = "Settle a sale: move locked serials into a buyer holding (idempotent)")
    public TransferResponse transfer(@PathVariable Long holdingId, @Valid @RequestBody TransferRequest request) {
        return TransferResponse.from(registryCommandUseCase.transferLockedCredits(request.idempotencyKey(),
                holdingId, request.buyerCompanyId(), request.quantity(), request.orderRef()));
    }
}
