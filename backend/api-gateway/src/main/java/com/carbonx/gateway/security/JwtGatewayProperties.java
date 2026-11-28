package com.carbonx.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Gateway JWT validation settings synchronized with identity-service issuer & HMAC key. */
@ConfigurationProperties(prefix = "carbonx.security.jwt")
public record JwtGatewayProperties(String secret, String issuer) {
}
