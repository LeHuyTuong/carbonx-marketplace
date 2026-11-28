package com.carbonx.mrv.api;

import com.carbonx.mrv.api.dto.MrvDtos.IdResponse;
import com.carbonx.mrv.api.dto.MrvDtos.ProjectApplicationResponse;
import com.carbonx.mrv.api.dto.MrvDtos.ReviewRequest;
import com.carbonx.mrv.api.dto.MrvDtos.SubmitApplicationRequest;
import com.carbonx.mrv.application.port.in.MrvCommandUseCase;
import com.carbonx.mrv.application.port.in.MrvQueryUseCase;
import com.carbonx.mrv.application.port.out.IdentityServiceClientPort;
import com.carbonx.mrv.application.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Project applications: company submission, then the CVA and Admin approval levels. */
@RestController
@RequestMapping({"/api/v1/applications", "/api/v1/project-applications"})
@RequiredArgsConstructor
@Tag(name = "MRV — Applications")
public class ApplicationController {

    private final MrvCommandUseCase mrvCommandUseCase;
    private final MrvQueryUseCase mrvQueryUseCase;
    private final ObjectProvider<IdentityServiceClientPort> identityLookupProvider;

    @GetMapping("/my")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Project applications submitted by the current company")
    public List<ProjectApplicationResponse> listMyApplications(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        return mrvQueryUseCase.listMyApplications(resolveCompanyId(caller)).stream()
                .map(ProjectApplicationResponse::from)
                .toList();
    }

    @GetMapping("/pending-cva")
    @PreAuthorize("hasRole('CVA')")
    @Operation(summary = "Project applications awaiting CVA review")
    public List<ProjectApplicationResponse> listPendingForCva() {
        return mrvQueryUseCase.listPendingApplicationsForCva().stream()
                .map(ProjectApplicationResponse::from)
                .toList();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "All project applications across companies (ADMIN only)")
    public List<ProjectApplicationResponse> listAll() {
        return mrvQueryUseCase.listAllApplications().stream()
                .map(ProjectApplicationResponse::from)
                .toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Submit a project application (snapshots the KYC-approved company name)")
    public IdResponse submitApplication(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @Valid @RequestBody SubmitApplicationRequest request) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        Long applicationId = mrvCommandUseCase.submitApplication(resolveCompanyId(caller), request.projectId(),
                request.documentUrl());
        return new IdResponse(applicationId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Submit a project application via multipart form")
    public IdResponse submitApplicationMultipart(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @RequestParam Long projectId,
                                                 @RequestPart(value = "file", required = false) MultipartFile file) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        String docRef = file != null && !file.isEmpty() ? file.getOriginalFilename() : null;
        Long applicationId = mrvCommandUseCase.submitApplication(resolveCompanyId(caller), projectId, docRef);
        return new IdResponse(applicationId);
    }

    @PostMapping("/{applicationId}/cva-review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CVA')")
    @Operation(summary = "First approval level: CVA reviews a SUBMITTED application")
    public void reviewByCva(@AuthenticationPrincipal AuthenticatedUser principal,
                            @PathVariable Long applicationId,
                            @Valid @RequestBody ReviewRequest request) {
        AuthenticatedUser cva = PrincipalGuard.require(principal);
        mrvCommandUseCase.reviewApplicationByCva(cva.userId(), applicationId, request.outcome(), request.note());
    }

    @PutMapping("/{applicationId}/cva-decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CVA')")
    @Operation(summary = "First approval level (query-param alias): CVA decides a SUBMITTED application")
    public void decideByCva(@AuthenticationPrincipal AuthenticatedUser principal,
                            @PathVariable Long applicationId,
                            @RequestParam boolean approved,
                            @RequestParam(required = false) String note) {
        AuthenticatedUser cva = PrincipalGuard.require(principal);
        mrvCommandUseCase.reviewApplicationByCva(cva.userId(), applicationId,
                approved ? "APPROVED" : "REJECTED", note);
    }

    @PostMapping("/{applicationId}/admin-review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Second approval level: ADMIN decides only after a CVA approval")
    public void reviewByAdmin(@AuthenticationPrincipal AuthenticatedUser principal,
                              @PathVariable Long applicationId,
                              @Valid @RequestBody ReviewRequest request) {
        AuthenticatedUser admin = PrincipalGuard.require(principal);
        mrvCommandUseCase.reviewApplicationByAdmin(admin.userId(), applicationId, request.outcome(), request.note());
    }

    @PutMapping("/{applicationId}/admin-decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Second approval level (query-param alias): ADMIN decides after CVA approval")
    public void decideByAdmin(@AuthenticationPrincipal AuthenticatedUser principal,
                              @PathVariable Long applicationId,
                              @RequestParam boolean approved,
                              @RequestParam(required = false) String note) {
        AuthenticatedUser admin = PrincipalGuard.require(principal);
        mrvCommandUseCase.reviewApplicationByAdmin(admin.userId(), applicationId,
                approved ? "APPROVED" : "REJECTED", note);
    }

    private Long resolveCompanyId(AuthenticatedUser caller) {
        if (caller.companyId() != null) {
            return caller.companyId();
        }
        IdentityServiceClientPort lookup = identityLookupProvider.getIfAvailable();
        return lookup != null ? lookup.resolveCompanyIdByUserId(caller.userId()) : caller.effectiveCompanyId();
    }
}
