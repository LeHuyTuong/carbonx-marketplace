package com.carbonx.identity.api;

import com.carbonx.identity.application.exception.IdentityApplicationException;
import com.carbonx.identity.application.security.AuthenticatedUser;

/** Fails closed when an authenticated endpoint is reached without a resolved JWT principal. */
public final class PrincipalGuard {

    private PrincipalGuard() {
    }

    public static AuthenticatedUser require(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw IdentityApplicationException.forbidden("Authentication is required");
        }
        return principal;
    }
}
