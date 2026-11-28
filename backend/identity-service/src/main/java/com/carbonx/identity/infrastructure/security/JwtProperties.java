package com.carbonx.identity.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Stateless JWT settings. The secret is fail-closed: it comes from JWT_SECRET and is never
 * given a default value in the build artifacts.
 */
@ConfigurationProperties(prefix = "carbonx.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {

    public JwtProperties {
        issuer = issuer == null || issuer.isBlank() ? "carbonx-identity" : issuer.trim();
        accessTokenTtl = accessTokenTtl == null ? Duration.ofHours(1) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(7) : refreshTokenTtl;
    }
}
