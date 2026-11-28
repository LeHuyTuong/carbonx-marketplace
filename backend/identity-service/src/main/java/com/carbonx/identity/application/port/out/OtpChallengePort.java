package com.carbonx.identity.application.port.out;

/**
 * Email OTP challenge port. Codes are delivered out-of-band by notification-service;
 * identity-service only verifies them, so no OTP state is persisted in this bounded context.
 */
public interface OtpChallengePort {
    boolean matches(String email, String otpCode);
}
