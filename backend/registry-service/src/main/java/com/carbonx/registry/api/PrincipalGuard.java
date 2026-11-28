package com.carbonx.registry.api;

import com.carbonx.registry.application.exception.RegistryApplicationException;
import com.carbonx.registry.application.security.AuthenticatedUser;

/** Fails closed when an authenticated endpoint is reached without a resolved JWT principal. */
public final class PrincipalGuard {

    private PrincipalGuard() {
    }

    public static AuthenticatedUser require(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw RegistryApplicationException.forbidden("Authentication is required");
        }
        return principal;
    }
}
