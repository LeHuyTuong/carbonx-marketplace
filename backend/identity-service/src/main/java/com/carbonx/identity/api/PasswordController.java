package com.carbonx.identity.api;

import com.carbonx.common.web.ApiResponse;
import com.carbonx.identity.api.dto.AdminDtos.ChangePasswordRequest;
import com.carbonx.identity.application.port.in.IdentityCommandUseCase;
import com.carbonx.identity.application.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated account password rotation endpoint used by the web and mobile clients. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Identity — Account Security")
public class PasswordController {

    private final IdentityCommandUseCase commandUseCase;

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change password for the currently signed-in account")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        AuthenticatedUser actor = PrincipalGuard.require(principal);
        commandUseCase.changePassword(actor.userId(), request.oldPassword(),
                request.newPassword(), request.confirmPassword());
        return ApiResponse.ok(MDC.get("traceId"), null);
    }
}
