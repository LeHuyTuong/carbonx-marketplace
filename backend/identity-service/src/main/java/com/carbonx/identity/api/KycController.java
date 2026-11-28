package com.carbonx.identity.api;

import com.carbonx.common.web.ApiResponse;
import com.carbonx.identity.api.dto.KycDtos.CompanyKycRequest;
import com.carbonx.identity.api.dto.KycDtos.CompanyKycResponse;
import com.carbonx.identity.api.dto.KycDtos.CompanyReviewRequest;
import com.carbonx.identity.api.dto.KycDtos.EvOwnerFullKycRequest;
import com.carbonx.identity.api.dto.KycDtos.EvOwnerKycRequest;
import com.carbonx.identity.api.dto.KycDtos.EvOwnerKycResponse;
import com.carbonx.identity.api.dto.KycDtos.StaffKycProfileResponse;
import com.carbonx.identity.application.exception.IdentityApplicationException;
import com.carbonx.identity.application.port.in.IdentityCommandUseCase;
import com.carbonx.identity.application.port.in.IdentityCommandUseCase.EvKycCommand;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase;
import com.carbonx.identity.application.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** KYC dossier submission, FE profile compatibility endpoints, and ADMIN review decisions. */
@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
@Tag(name = "Identity — KYC")
public class KycController {

    private final IdentityCommandUseCase identityCommandUseCase;
    private final IdentityQueryUseCase identityQueryUseCase;

    @PostMapping("/ev-owner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('EV_OWNER')")
    @Operation(summary = "Submit or resubmit the EV owner KYC dossier")
    public void submitEvOwnerKyc(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @Valid @RequestBody EvOwnerKycRequest request) {
        AuthenticatedUser currentUser = PrincipalGuard.require(principal);
        identityCommandUseCase.submitEvOwnerKyc(currentUser.userId(), request.documentType(),
                request.documentNumber(), request.phone());
    }

    @GetMapping("/user")
    @PreAuthorize("hasRole('EV_OWNER')")
    @Operation(summary = "Read the signed-in EV owner KYC profile")
    public ApiResponse<EvOwnerKycResponse> getMyEvKyc(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedUser currentUser = PrincipalGuard.require(principal);
        return ApiResponse.ok(MDC.get("traceId"),
                EvOwnerKycResponse.from(identityQueryUseCase.getMyEvKyc(currentUser.userId())));
    }

    @PostMapping({"/user", "/kycEv"})
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EV_OWNER')")
    @Operation(summary = "Submit full EV owner KYC dossier from the web form")
    public ApiResponse<EvOwnerKycResponse> submitFullEvKyc(@AuthenticationPrincipal AuthenticatedUser principal,
                                                           @Valid @RequestBody EvOwnerFullKycRequest request) {
        AuthenticatedUser currentUser = PrincipalGuard.require(principal);
        identityCommandUseCase.submitEvOwnerKyc(currentUser.userId(), new EvKycCommand(
                request.name(), request.phone(), request.country(), request.address(),
                request.birthDate(), request.documentType(), request.documentNumber(), request.gender()));
        return ApiResponse.ok(MDC.get("traceId"),
                EvOwnerKycResponse.from(identityQueryUseCase.getMyEvKyc(currentUser.userId())));
    }

    @PutMapping("/user")
    @PreAuthorize("hasRole('EV_OWNER')")
    @Operation(summary = "Update the signed-in EV owner KYC dossier")
    public ApiResponse<EvOwnerKycResponse> updateFullEvKyc(@AuthenticationPrincipal AuthenticatedUser principal,
                                                           @Valid @RequestBody EvOwnerFullKycRequest request) {
        return submitFullEvKyc(principal, request);
    }

    @GetMapping("/user/listKYC")
    @PreAuthorize("hasAnyRole('ADMIN','CVA')")
    @Operation(summary = "List EV owner KYC dossiers for Admin/CVA directory")
    public ApiResponse<List<EvOwnerKycResponse>> listEvKycDirectory() {
        return ApiResponse.ok(MDC.get("traceId"), identityQueryUseCase.listAllEvKyc().stream()
                .map(EvOwnerKycResponse::from)
                .toList());
    }

    @GetMapping("/company")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Read the signed-in company KYC profile")
    public ApiResponse<CompanyKycResponse> getMyCompanyKyc(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedUser currentUser = PrincipalGuard.require(principal);
        return ApiResponse.ok(MDC.get("traceId"),
                CompanyKycResponse.from(identityQueryUseCase.getCompanyByUserId(currentUser.userId())));
    }

    @PostMapping("/company")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Submit or resubmit the company KYC dossier")
    public void submitCompanyKyc(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @Valid @RequestBody CompanyKycRequest request) {
        upsertCompanyKyc(principal, request);
    }

    @PutMapping("/company")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Update the signed-in company KYC dossier")
    public void updateCompanyKyc(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @Valid @RequestBody CompanyKycRequest request) {
        upsertCompanyKyc(principal, request);
    }

    @GetMapping("/company/listKYCCompany")
    @PreAuthorize("hasAnyRole('ADMIN','CVA','EV_OWNER','COMPANY')")
    @Operation(summary = "List registered company KYC dossiers")
    public ApiResponse<List<CompanyKycResponse>> listCompanyDirectory() {
        return ApiResponse.ok(MDC.get("traceId"), identityQueryUseCase.listAllCompanies().stream()
                .map(CompanyKycResponse::from)
                .toList());
    }

    @GetMapping("/cva")
    @PreAuthorize("hasRole('CVA')")
    @Operation(summary = "Read the signed-in CVA officer profile")
    public ApiResponse<StaffKycProfileResponse> getMyCvaProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedUser currentUser = PrincipalGuard.require(principal);
        return ApiResponse.ok(MDC.get("traceId"),
                StaffKycProfileResponse.from(identityQueryUseCase.getUserProfile(currentUser.userId())));
    }

    @GetMapping("/cva/list")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all CVA officer profiles for Admin directory")
    public ApiResponse<List<StaffKycProfileResponse>> listCvas() {
        return ApiResponse.ok(MDC.get("traceId"), identityQueryUseCase.listCvas().stream()
                .map(StaffKycProfileResponse::from)
                .toList());
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Read the signed-in Admin profile")
    public ApiResponse<StaffKycProfileResponse> getMyAdminProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedUser currentUser = PrincipalGuard.require(principal);
        return ApiResponse.ok(MDC.get("traceId"),
                StaffKycProfileResponse.from(identityQueryUseCase.getUserProfile(currentUser.userId())));
    }

    @PostMapping("/companies/{companyId}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve or reject a company KYC dossier")
    public void reviewCompanyKyc(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @PathVariable Long companyId,
                                 @Valid @RequestBody CompanyReviewRequest request) {
        AuthenticatedUser currentUser = PrincipalGuard.require(principal);
        identityCommandUseCase.reviewCompanyKyc(currentUser.userId(), companyId,
                Boolean.TRUE.equals(request.approved()), request.note());
    }

    private void upsertCompanyKyc(AuthenticatedUser principal, CompanyKycRequest request) {
        AuthenticatedUser currentUser = PrincipalGuard.require(principal);
        String businessName = request.resolvedBusinessName();
        if (businessName == null || businessName.isBlank()) {
            throw IdentityApplicationException.badRequest("businessName is required");
        }
        identityCommandUseCase.submitCompanyKyc(currentUser.userId(), businessName,
                request.resolvedTaxCode(), request.resolvedBusinessLicense(), request.resolvedAddress());
    }
}
