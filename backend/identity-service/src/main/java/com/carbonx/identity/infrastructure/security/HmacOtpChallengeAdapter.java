package com.carbonx.identity.infrastructure.security;

import com.carbonx.identity.application.port.out.OtpChallengePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;

/**
 * Stateless, time-windowed OTP challenge derived from the service signing secret: no OTP row
 * is stored in the identity schema. Codes are six digits over a 10 minute window and are
 * delivered by notification-service; verification is constant-time.
 */
@Component
@RequiredArgsConstructor
public class HmacOtpChallengeAdapter implements OtpChallengePort {

    private static final int WINDOW_MINUTES = 10;
    private static final int CODE_MODULUS = 1_000_000;
    private static final String ALGORITHM = "HmacSHA256";

    private final JwtSigningKeyHolder signingKey;

    @Override
    public boolean matches(String email, String otpCode) {
        if (email == null || email.isBlank() || otpCode == null || otpCode.isBlank()) {
            return false;
        }
        byte[] supplied = otpCode.trim().getBytes(StandardCharsets.UTF_8);
        long currentMinute = Instant.now().getEpochSecond() / 60;
        for (long offset = 0; offset <= WINDOW_MINUTES; offset++) {
            byte[] expected = code(email, currentMinute - offset).getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expected, supplied)) {
                return true;
            }
        }
        return false;
    }

    private String code(String email, long minute) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(signingKey.key());
            byte[] digest = mac.doFinal((email.trim().toLowerCase(Locale.ROOT) + '|' + minute)
                    .getBytes(StandardCharsets.UTF_8));
            int value = Math.floorMod(ByteBuffer.wrap(digest).getInt(), CODE_MODULUS);
            return String.format(Locale.ROOT, "%06d", value);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot compute OTP challenge", ex);
        }
    }
}
