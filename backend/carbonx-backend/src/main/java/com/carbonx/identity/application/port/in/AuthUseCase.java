package com.carbonx.identity.application.port.in;

/** Inbound Use-Case Port: Authentication & Account Lifecycle. */
public interface AuthUseCase {
    AuthResult register(RegisterCommand command);

    AuthResult login(LoginCommand command);

    void verifyOtp(VerifyOtpCommand command);

    AuthResult refreshToken(String refreshToken);

    void changePassword(Long userId, ChangePasswordCommand command);

    record RegisterCommand(String email, String password, String fullName, String role) {}

    record LoginCommand(String email, String password) {}

    record VerifyOtpCommand(String email, String otpCode) {}

    record ChangePasswordCommand(String currentPassword, String newPassword) {}

    record AuthResult(String accessToken, String refreshToken, Long userId, String email, String role) {}
}
