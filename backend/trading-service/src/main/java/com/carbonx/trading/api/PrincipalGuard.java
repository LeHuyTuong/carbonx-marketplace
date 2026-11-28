package com.carbonx.trading.api;

import com.carbonx.trading.application.exception.TradingApplicationException;
import com.carbonx.trading.application.security.AuthenticatedUser;

/** Fails closed when an authenticated endpoint is reached without a resolved JWT principal. */
public final class PrincipalGuard {

    private PrincipalGuard() {
    }

    public static AuthenticatedUser require(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw TradingApplicationException.forbidden("Authentication is required");
        }
        return principal;
    }
}
