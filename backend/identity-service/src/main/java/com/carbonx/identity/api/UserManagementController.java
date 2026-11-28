package com.carbonx.identity.api;

import com.carbonx.common.web.ApiResponse;
import com.carbonx.identity.api.dto.AdminDtos.AdminCreateUserRequest;
import com.carbonx.identity.api.dto.AdminDtos.AdminUserResponse;
import com.carbonx.identity.api.dto.AdminDtos.UpdateUserStatusRequest;
import com.carbonx.identity.application.port.in.IdentityCommandUseCase;
import com.carbonx.identity.application.port.in.IdentityCommandUseCase.AdminProvisionUserCommand;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase;
import com.carbonx.identity.application.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin user management and staff provisioning endpoints ({@code ROLE_CVA} / {@code ROLE_ADMIN})
 * per BR-08.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Identity — Admin Users")
public class UserManagementController {

    private final IdentityCommandUseCase commandUseCase;
    private final IdentityQueryUseCase queryUseCase;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all registered platform accounts for Admin user management")
    public ApiResponse<List<AdminUserResponse>> listUsers(@AuthenticationPrincipal AuthenticatedUser principal) {
        PrincipalGuard.require(principal);
        List<AdminUserResponse> users = queryUseCase.listAllUsersForAdmin().stream()
                .map(user -> new AdminUserResponse(
                        user.id(),
                        user.email(),
                        user.fullName(),
                        user.role(),
                        user.status(),
                        user.createdAt()))
                .toList();
        return ApiResponse.ok(MDC.get("traceId"), users);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Provision a CVA, ADMIN, COMPANY, or EV_OWNER user account as Administrator")
    public ResponseEntity<ApiResponse<AdminUserResponse>> createUser(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AdminCreateUserRequest request) {
        AuthenticatedUser admin = PrincipalGuard.require(principal);
        var created = commandUseCase.provisionUserByAdmin(admin.userId(), new AdminProvisionUserCommand(
                request.email(),
                request.password(),
                request.fullName(),
                request.role()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(MDC.get("traceId"), new AdminUserResponse(
                        created.id(),
                        created.email(),
                        created.fullName(),
                        created.role(),
                        created.status(),
                        created.createdAt())));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate or suspend a user account")
    public ApiResponse<AdminUserResponse> updateStatus(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestBody(required = false) UpdateUserStatusRequest body) {
        AuthenticatedUser admin = PrincipalGuard.require(principal);
        String nextStatus = body != null && body.status() != null && !body.status().isBlank()
                ? body.status()
                : status;
        var updated = commandUseCase.updateUserStatus(admin.userId(), id, nextStatus);
        return ApiResponse.ok(MDC.get("traceId"), new AdminUserResponse(
                updated.id(),
                updated.email(),
                updated.fullName(),
                updated.role(),
                updated.status(),
                updated.createdAt()));
    }
}
