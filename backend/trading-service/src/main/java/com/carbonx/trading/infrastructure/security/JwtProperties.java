package com.carbonx.trading.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stateless JWT settings shared with identity-service. The secret is fail-closed: it comes from
 * JWT_SECRET and is never given a default value in the build artifacts.
 */
@ConfigurationProperties(prefix = "carbonx.security.jwt")
public record JwtProperties(String secret, String issuer) {

    public JwtProperties {
        issuer = issuer == null || issuer.isBlank() ? "carbonx-identity" : issuer.trim();
    }
}
