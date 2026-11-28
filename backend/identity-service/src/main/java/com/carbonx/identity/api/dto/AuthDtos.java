package com.carbonx.identity.api.dto;

import com.carbonx.identity.application.port.in.IdentityCommandUseCase.AuthResult;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Explicit API contracts for the authentication endpoints (no entity leaks, no framework types). */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 191) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 191) String fullName,
            @NotBlank @Size(max = 32) String role) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 191) String email,
            @NotBlank @Size(max = 72) String password) {
    }

    public record VerifyOtpRequest(
            @NotBlank @Email @Size(max = 191) String email,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "OTP must be 6 digits") String otpCode) {
    }

    public record AuthResponse(String accessToken, String refreshToken, String jwt, String token,
                               Long userId, Long companyId, String email, String role) {
        public static AuthResponse from(AuthResult result) {
            return new AuthResponse(result.accessToken(), result.refreshToken(),
                    result.accessToken(), result.accessToken(),
                    result.userId(), result.companyId(), result.email(), result.role());
        }
    }
}
