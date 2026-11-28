package com.carbonx.trading.infrastructure.security;

import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Holds the HMAC-SHA256 key derived from {@code carbonx.security.jwt.secret}. Startup fails instead
 * of falling back to a baked-in secret when the value is missing or too short.
 */
@Component
public class JwtSigningKeyHolder {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;

    public JwtSigningKeyHolder(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "carbonx.security.jwt.secret (JWT_SECRET) must be at least " + MIN_SECRET_BYTES
                            + " bytes; refusing to start with a weak or missing signing secret");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public SecretKey key() {
        return key;
    }
}
