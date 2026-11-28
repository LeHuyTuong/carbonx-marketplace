package com.carbonx.ledger.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carbonx.security.jwt")
public record JwtProperties(String secret, String issuer) {
    public JwtProperties {
        issuer = issuer == null || issuer.isBlank() ? "carbonx-identity" : issuer.trim();
    }
}
