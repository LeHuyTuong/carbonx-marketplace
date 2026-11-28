package com.carbonx.identity.api;

import com.carbonx.identity.api.dto.VehicleDtos.CompanySummaryResponse;
import com.carbonx.identity.api.dto.VehicleDtos.VehicleRegistrationRequest;
import com.carbonx.identity.api.dto.VehicleDtos.VehicleRegisteredResponse;
import com.carbonx.identity.api.dto.VehicleDtos.VehicleResponse;
import com.carbonx.identity.application.port.in.IdentityCommandUseCase;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase.CompanyEvOwnerSummaryView;
import com.carbonx.identity.application.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Vehicle registration and listings. Role checks live on the server: owners may only touch their
 * own vehicles, and the platform-wide listing is ADMIN-only.
 */
@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@Tag(name = "Identity — Vehicles")
public class VehicleController {

    private final IdentityCommandUseCase identityCommandUseCase;
    private final IdentityQueryUseCase identityQueryUseCase;

    @PostMapping(value = "/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EV_OWNER')")
    @Operation(summary = "Register a vehicle under an approved company (publishes VehicleRegisteredEvent)")
    public VehicleRegisteredResponse registerVehicle(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @Valid @RequestBody VehicleRegistrationRequest request) {
        AuthenticatedUser currentUser = PrincipalGuard.require(principal);
        Long vehicleId = identityCommandUseCase.registerVehicle(currentUser.userId(), request.companyId(),
                request.plateNumber(), request.brand(), request.model(), request.documentUrl());
        return new VehicleRegisteredResponse(vehicleId);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EV_OWNER')")
    @Operation(summary = "Register a vehicle via JSON payload")
    public VehicleRegisteredResponse registerVehicleJson(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @Valid @RequestBody VehicleRegistrationRequest request) {
        return registerVehicle(principal, request);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EV_OWNER')")
    @Operation(summary = "Register a vehicle via multipart form-data with registration document")
    public VehicleRegisteredResponse registerVehicleMultipart(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam Long companyId,
            @RequestParam String plateNumber,
            @RequestParam String brand,
            @RequestParam String model,
            @RequestParam(required = false) MultipartFile documentFile,
            @RequestParam(required = false) String documentUrl) {
        AuthenticatedUser currentUser = PrincipalGuard.require(principal);
        String resolvedUrl = resolveDocumentUrl(documentFile, documentUrl);
        Long vehicleId = identityCommandUseCase.registerVehicle(currentUser.userId(), companyId,
                plateNumber, brand, model, resolvedUrl);
        return new VehicleRegisteredResponse(vehicleId);
    }

    @PutMapping(value = "/{vehicleId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('EV_OWNER')")
    @Operation(summary = "Update a registered vehicle owned by the current EV owner (JSON)")
    public VehicleRegisteredResponse updateVehicleJson(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long vehicleId,
            @Valid @RequestBody VehicleRegistrationRequest request) {
        AuthenticatedUser currentUser = PrincipalGuard.require(principal);
        Long updatedId = identityCommandUseCase.updateVehicle(currentUser.userId(), vehicleId,
                request.companyId(), request.plateNumber(), request.brand(), request.model(), request.documentUrl());
        return new VehicleRegisteredResponse(updatedId);
    }

    @PutMapping(value = "/{vehicleId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('EV_OWNER')")
    @Operation(summary = "Update a registered vehicle owned by the current EV owner (multipart)")
    public VehicleRegisteredResponse updateVehicleMultipart(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long vehicleId,
            @RequestParam Long companyId,
            @RequestParam String plateNumber,
            @RequestParam String brand,
            @RequestParam String model,
            @RequestParam(required = false) MultipartFile documentFile,
            @RequestParam(required = false) String documentUrl) {
        AuthenticatedUser currentUser = PrincipalGuard.require(principal);
        String resolvedUrl = resolveDocumentUrl(documentFile, documentUrl);
        Long updatedId = identityCommandUseCase.updateVehicle(currentUser.userId(), vehicleId,
                companyId, plateNumber, brand, model, resolvedUrl);
        return new VehicleRegisteredResponse(updatedId);
    }

    @GetMapping({"", "/my"})
    @PreAuthorize("hasRole('EV_OWNER')")
    @Operation(summary = "List the vehicles owned by the current EV owner")
    public List<VehicleResponse> listMyVehicles(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedUser currentUser = PrincipalGuard.require(principal);
        return identityQueryUseCase.listMyVehicles(currentUser.userId()).stream()
                .map(VehicleResponse::from)
                .toList();
    }

    @GetMapping("/company/summary")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "List EV owners and their registered vehicles aggregated under the current company")
    public List<CompanyEvOwnerSummaryView> companyVehiclesSummary(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedUser currentUser = PrincipalGuard.require(principal);
        return identityQueryUseCase.listCompanyEvOwnersSummary(currentUser.userId());
    }

    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List every registered vehicle (ADMIN only)")
    public List<VehicleResponse> listAllVehicles(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) Integer pageNo,
                                                 @RequestParam(required = false) Integer pageSize) {
        int resolvedPage = pageNo != null ? pageNo : page;
        int resolvedSize = pageSize != null ? pageSize : size;
        return identityQueryUseCase.listAllVehiclesForAdmin(resolvedPage, resolvedSize).stream()
                .map(VehicleResponse::from)
                .toList();
    }

    @DeleteMapping("/{vehicleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('EV_OWNER','ADMIN')")
    @Operation(summary = "Delete a vehicle — owners only for their own vehicles, ADMIN for any")
    public void deleteVehicle(@AuthenticationPrincipal AuthenticatedUser principal,
                              @PathVariable Long vehicleId) {
        AuthenticatedUser currentUser = PrincipalGuard.require(principal);
        identityCommandUseCase.deleteVehicle(currentUser.userId(), currentUser.role(), vehicleId);
    }

    private static String resolveDocumentUrl(MultipartFile documentFile, String fallbackUrl) {
        if (documentFile != null && documentFile.getOriginalFilename() != null
                && !documentFile.getOriginalFilename().isBlank()) {
            return "upload://" + documentFile.getOriginalFilename().trim();
        }
        return fallbackUrl;
    }
}
