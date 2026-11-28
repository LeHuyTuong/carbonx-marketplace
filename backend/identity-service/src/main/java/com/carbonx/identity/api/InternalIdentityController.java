package com.carbonx.identity.api;

import com.carbonx.identity.api.dto.VehicleDtos.CompanySummaryResponse;
import com.carbonx.identity.api.dto.VehicleDtos.PlateVerificationRequest;
import com.carbonx.identity.api.dto.VehicleDtos.PlateVerificationResponse;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal contracts used by downstream services via OpenFeign. Guarded by
 * {@code X-Internal-Service-Token} ({@code ROLE_INTERNAL_SERVICE}) and not routed by api-gateway.
 */
@RestController
@RequestMapping("/api/v1/internal")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
@RequiredArgsConstructor
@Tag(name = "Identity — Internal")
public class InternalIdentityController {

    private final IdentityQueryUseCase identityQueryUseCase;

    @GetMapping("/companies/{companyId}")
    @Operation(summary = "Company snapshot for another service (name, tax code, KYC status)")
    public CompanySummaryResponse getCompany(@PathVariable Long companyId) {
        return CompanySummaryResponse.from(identityQueryUseCase.getCompanyById(companyId));
    }

    @GetMapping("/companies/by-user/{userId}")
    @Operation(summary = "Resolve canonical companies.id from users.id across Bounded Contexts")
    public CompanySummaryResponse getCompanyByUserId(@PathVariable Long userId) {
        return CompanySummaryResponse.from(identityQueryUseCase.getCompanyByUserId(userId));
    }

    @PostMapping("/vehicles/verify-plates")
    @Operation(summary = "Return which of the supplied plate numbers are registered to a company")
    public PlateVerificationResponse verifyPlates(@Valid @RequestBody PlateVerificationRequest request) {
        return new PlateVerificationResponse(request.companyId(),
                identityQueryUseCase.listVehiclesByPlates(request.companyId(), request.plateNumbers()).stream()
                        .map(vehicle -> vehicle.plateNumber())
                        .toList());
    }
}
