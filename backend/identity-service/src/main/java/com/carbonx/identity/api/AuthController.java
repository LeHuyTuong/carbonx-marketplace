package com.carbonx.identity.api;

import com.carbonx.identity.api.dto.AuthDtos.AuthResponse;
import com.carbonx.identity.api.dto.AuthDtos.LoginRequest;
import com.carbonx.identity.api.dto.AuthDtos.RegisterRequest;
import com.carbonx.identity.api.dto.AuthDtos.VerifyOtpRequest;
import com.carbonx.identity.application.port.in.IdentityCommandUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public authentication entry points (the Google OIDC handshake is handled by Spring Security). */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Identity — Authentication")
public class AuthController {

    private final IdentityCommandUseCase identityCommandUseCase;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register an EV owner or a company account")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return AuthResponse.from(identityCommandUseCase.register(new IdentityCommandUseCase.RegisterCommand(
                request.email(), request.password(), request.fullName(), request.role())));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return AuthResponse.from(identityCommandUseCase.login(
                new IdentityCommandUseCase.LoginCommand(request.email(), request.password())));
    }

    @PostMapping("/verify-otp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Verify the email OTP challenge and activate the account")
    public void verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        identityCommandUseCase.verifyOtp(request.email(), request.otpCode());
    }
}
