package com.carbonx.mrv.api;

import com.carbonx.mrv.api.dto.MrvDtos.CreateProjectRequest;
import com.carbonx.mrv.api.dto.MrvDtos.IdResponse;
import com.carbonx.mrv.api.dto.MrvDtos.ProjectResponse;
import com.carbonx.mrv.api.dto.MrvDtos.UpdateProjectRequest;
import com.carbonx.mrv.application.port.in.MrvCommandUseCase;
import com.carbonx.mrv.application.port.in.MrvQueryUseCase;
import com.carbonx.mrv.application.security.AuthenticatedUser;
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

import java.math.BigDecimal;
import java.util.List;

/** VM0038 project catalogue and administration. */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "MRV — Projects")
public class ProjectController {

    private final MrvCommandUseCase mrvCommandUseCase;
    private final MrvQueryUseCase mrvQueryUseCase;

    @GetMapping({"", "/all"})
    @Operation(summary = "List all VM0038 projects (public)")
    public List<ProjectResponse> listProjects() {
        return mrvQueryUseCase.listProjects().stream().map(ProjectResponse::from).toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a VM0038 project with its methodology constants (ADMIN only)")
    public IdResponse createProject(@AuthenticationPrincipal AuthenticatedUser principal,
                                    @Valid @RequestBody CreateProjectRequest request) {
        AuthenticatedUser admin = PrincipalGuard.require(principal);
        Long projectId = mrvCommandUseCase.createProject(admin.userId(), request.title(), request.description(),
                request.emissionFactor(), request.bufferRate(), request.uncertaintyRate(), request.leakageRate());
        return new IdResponse(projectId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a VM0038 project via multipart form (ADMIN only)")
    public IdResponse createProjectMultipart(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) BigDecimal emissionFactor,
            @RequestParam(required = false) BigDecimal emissionFactorKgPerKwh,
            @RequestParam(required = false) BigDecimal bufferRate,
            @RequestParam(required = false) BigDecimal bufferReservePct,
            @RequestParam(required = false) BigDecimal uncertaintyRate,
            @RequestParam(required = false) BigDecimal uncertaintyPct,
            @RequestParam(required = false) BigDecimal leakageRate,
            @RequestParam(required = false) BigDecimal leakagePct) {
        AuthenticatedUser admin = PrincipalGuard.require(principal);
        Long projectId = mrvCommandUseCase.createProject(
                admin.userId(),
                title,
                description,
                emissionFactor != null ? emissionFactor : emissionFactorKgPerKwh,
                bufferRate != null ? bufferRate : toRate(bufferReservePct),
                uncertaintyRate != null ? uncertaintyRate : toRate(uncertaintyPct),
                leakageRate != null ? leakageRate : toRate(leakagePct));
        return new IdResponse(projectId);
    }

    @PutMapping(value = "/{projectId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a VM0038 project (ADMIN only)")
    public void updateProject(@AuthenticationPrincipal AuthenticatedUser principal,
                              @PathVariable Long projectId,
                              @Valid @RequestBody UpdateProjectRequest request) {
        AuthenticatedUser admin = PrincipalGuard.require(principal);
        mrvCommandUseCase.updateProject(admin.userId(), projectId, request.title(), request.description(),
                request.status(), request.emissionFactor(), request.bufferRate(),
                request.uncertaintyRate(), request.leakageRate());
    }

    @PutMapping(value = "/{projectId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a VM0038 project via multipart form (ADMIN only)")
    public void updateProjectMultipart(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long projectId,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) BigDecimal emissionFactor,
            @RequestParam(required = false) BigDecimal emissionFactorKgPerKwh,
            @RequestParam(required = false) BigDecimal bufferRate,
            @RequestParam(required = false) BigDecimal bufferReservePct,
            @RequestParam(required = false) BigDecimal uncertaintyRate,
            @RequestParam(required = false) BigDecimal uncertaintyPct,
            @RequestParam(required = false) BigDecimal leakageRate,
            @RequestParam(required = false) BigDecimal leakagePct) {
        AuthenticatedUser admin = PrincipalGuard.require(principal);
        mrvCommandUseCase.updateProject(
                admin.userId(),
                projectId,
                title,
                description,
                status,
                emissionFactor != null ? emissionFactor : emissionFactorKgPerKwh,
                bufferRate != null ? bufferRate : toRate(bufferReservePct),
                uncertaintyRate != null ? uncertaintyRate : toRate(uncertaintyPct),
                leakageRate != null ? leakageRate : toRate(leakagePct));
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a VM0038 project (ADMIN only)")
    public void deleteProject(@AuthenticationPrincipal AuthenticatedUser principal,
                              @PathVariable Long projectId) {
        AuthenticatedUser admin = PrincipalGuard.require(principal);
        mrvCommandUseCase.deleteProject(admin.userId(), projectId);
    }

    private static BigDecimal toRate(BigDecimal percentageOrRate) {
        if (percentageOrRate == null) {
            return null;
        }
        return percentageOrRate.compareTo(BigDecimal.ONE) > 0
                ? percentageOrRate.movePointLeft(2)
                : percentageOrRate;
    }
}
