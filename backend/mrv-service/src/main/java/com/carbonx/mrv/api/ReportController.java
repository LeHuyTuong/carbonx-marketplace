package com.carbonx.mrv.api;

import com.carbonx.mrv.api.dto.MrvDtos.AiScoreResponse;
import com.carbonx.mrv.api.dto.MrvDtos.EmissionReportResponse;
import com.carbonx.mrv.api.dto.MrvDtos.IdResponse;
import com.carbonx.mrv.api.dto.MrvDtos.VerifyReportRequest;
import com.carbonx.mrv.application.exception.MrvApplicationException;
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

import java.io.IOException;
import java.util.List;

/**
 * Emission report workflow: COMPANY uploads activity data (DQ1..DQ8 are evaluated and stored),
 * CVA asks for an AI/DQ pre-score and verifies, ADMIN approves which publishes
 * {@code EmissionReportApprovedEvent} to the {@code mrv.events} topic.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "MRV — Emission Reports")
public class ReportController {

    private final MrvCommandUseCase mrvCommandUseCase;
    private final MrvQueryUseCase mrvQueryUseCase;
    private final ObjectProvider<IdentityServiceClientPort> identityLookupProvider;

    @PostMapping(value = {"", "/upload"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Upload activity-data CSV for an approved project (runs the DQ1..DQ8 rules)")
    public IdResponse uploadReport(@AuthenticationPrincipal AuthenticatedUser principal,
                                   @RequestParam Long projectId,
                                   @RequestPart("file") MultipartFile file) {
        AuthenticatedUser company = PrincipalGuard.require(principal);
        if (file == null || file.isEmpty()) {
            throw MrvApplicationException.badRequest("A non-empty CSV file is required");
        }
        try (var stream = file.getInputStream()) {
            Long reportId = mrvCommandUseCase.uploadCsvReport(resolveCompanyId(company), projectId,
                    file.getOriginalFilename(), stream);
            return new IdResponse(reportId);
        } catch (IOException ex) {
            throw MrvApplicationException.badRequest("Cannot read the uploaded CSV file");
        }
    }

    @PostMapping("/{reportId}/ai-score")
    @PreAuthorize("hasRole('CVA')")
    @Operation(summary = "AI pre-score for the CVA (Resilience4j aiScoring; falls back to DQ1..DQ8)")
    public AiScoreResponse aiScore(@AuthenticationPrincipal AuthenticatedUser principal,
                                   @PathVariable Long reportId) {
        AuthenticatedUser cva = PrincipalGuard.require(principal);
        return AiScoreResponse.from(mrvCommandUseCase.suggestAiScoreForCva(cva.userId(), reportId));
    }

    @PostMapping("/{reportId}/cva-verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CVA')")
    @Operation(summary = "First verification level: CVA verifies a SUBMITTED report")
    public void verifyByCva(@AuthenticationPrincipal AuthenticatedUser principal,
                            @PathVariable Long reportId,
                            @Valid @RequestBody VerifyReportRequest request) {
        AuthenticatedUser cva = PrincipalGuard.require(principal);
        mrvCommandUseCase.verifyReportByCva(cva.userId(), reportId, request.approved(), request.note());
    }

    @PutMapping("/{reportId}/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CVA')")
    @Operation(summary = "First verification level (query-param alias): CVA verifies a SUBMITTED report")
    public void verifyByCvaPut(@AuthenticationPrincipal AuthenticatedUser principal,
                               @PathVariable Long reportId,
                               @RequestParam boolean approved,
                               @RequestParam(required = false) String comment,
                               @RequestParam(required = false) String note) {
        AuthenticatedUser cva = PrincipalGuard.require(principal);
        mrvCommandUseCase.verifyReportByCva(cva.userId(), reportId, approved, note != null ? note : comment);
    }

    @PostMapping("/{reportId}/admin-approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Second level: ADMIN approves after CVA verification (publishes the MRV event)")
    public void approveByAdmin(@AuthenticationPrincipal AuthenticatedUser principal,
                               @PathVariable Long reportId,
                               @Valid @RequestBody VerifyReportRequest request) {
        AuthenticatedUser admin = PrincipalGuard.require(principal);
        mrvCommandUseCase.approveReportByAdmin(admin.userId(), reportId, request.approved(), request.note());
    }

    @PutMapping("/{reportId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Second level (query-param alias): ADMIN approves after CVA verification")
    public void approveByAdminPut(@AuthenticationPrincipal AuthenticatedUser principal,
                                  @PathVariable Long reportId,
                                  @RequestParam boolean approved,
                                  @RequestParam(required = false) String note) {
        AuthenticatedUser admin = PrincipalGuard.require(principal);
        mrvCommandUseCase.approveReportByAdmin(admin.userId(), reportId, approved, note);
    }

    @GetMapping({"/my", "/my-reports"})
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Reports uploaded by the current company")
    public List<EmissionReportResponse> listMyReports(@AuthenticationPrincipal AuthenticatedUser principal) {
        AuthenticatedUser company = PrincipalGuard.require(principal);
        return mrvQueryUseCase.listMyReports(resolveCompanyId(company)).stream()
                .map(EmissionReportResponse::from)
                .toList();
    }

    @GetMapping({"/pending-cva", "/list-cva-check"})
    @PreAuthorize("hasRole('CVA')")
    @Operation(summary = "Reports awaiting CVA verification")
    public List<EmissionReportResponse> listPendingForCva() {
        return mrvQueryUseCase.listPendingReportsForCva().stream()
                .map(EmissionReportResponse::from)
                .toList();
    }

    @GetMapping({"/admin", "/list"})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Every report with its current verification state (ADMIN only)")
    public List<EmissionReportResponse> listForAdmin() {
        return mrvQueryUseCase.listReportsForAdmin().stream()
                .map(EmissionReportResponse::from)
                .toList();
    }

    @GetMapping("/{reportId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CVA', 'COMPANY')")
    @Operation(summary = "Get a single emission report by ID")
    public EmissionReportResponse getReportById(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable Long reportId) {
        AuthenticatedUser caller = PrincipalGuard.require(principal);
        boolean isReviewerOrAdmin = caller.hasRole("ADMIN") || caller.hasRole("CVA");
        Long companyId = isReviewerOrAdmin ? null : resolveCompanyId(caller);
        return EmissionReportResponse.from(
                mrvQueryUseCase.getReportById(reportId, companyId, isReviewerOrAdmin));
    }

    private Long resolveCompanyId(AuthenticatedUser caller) {
        if (caller.companyId() != null) {
            return caller.companyId();
        }
        IdentityServiceClientPort lookup = identityLookupProvider.getIfAvailable();
        return lookup != null ? lookup.resolveCompanyIdByUserId(caller.userId()) : caller.effectiveCompanyId();
    }
}
