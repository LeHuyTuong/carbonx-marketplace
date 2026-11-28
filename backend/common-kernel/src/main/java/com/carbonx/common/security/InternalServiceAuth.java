package com.carbonx.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shared fail-closed contract for service-to-service calls under {@code /api/v1/internal/**}.
 * Every inter-service client must supply {@link #HEADER_NAME}, preventing external end-user
 * JWTs from directly invoking internal saga/verification endpoints.
 */
public final class InternalServiceAuth {

    public static final String HEADER_NAME = "X-Internal-Service-Token";
    public static final String ROLE_NAME = "INTERNAL_SERVICE";
    public static final String AUTHORITY = "ROLE_INTERNAL_SERVICE";
    private static final int MIN_SECRET_BYTES = 32;

    private InternalServiceAuth() {
    }

    /** Returns true only when both expected and supplied tokens are >= 32 bytes and match in constant time. */
    public static boolean matches(String expectedToken, String suppliedToken) {
        if (expectedToken == null || suppliedToken == null) {
            return false;
        }
        byte[] expectedBytes = expectedToken.trim().getBytes(StandardCharsets.UTF_8);
        byte[] suppliedBytes = suppliedToken.trim().getBytes(StandardCharsets.UTF_8);
        if (expectedBytes.length < MIN_SECRET_BYTES) {
            return false;
        }
        return MessageDigest.isEqual(expectedBytes, suppliedBytes);
    }
}
