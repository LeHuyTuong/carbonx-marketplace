package com.carbonx.mrv.api;

import com.carbonx.mrv.application.exception.MrvApplicationException;
import com.carbonx.mrv.application.security.AuthenticatedUser;

/** Fails closed when an authenticated endpoint is reached without a resolved JWT principal. */
public final class PrincipalGuard {

    private PrincipalGuard() {
    }

    public static AuthenticatedUser require(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw MrvApplicationException.forbidden("Authentication is required");
        }
        return principal;
    }
}
