package com.carbonx.ledger.api;

import com.carbonx.ledger.application.exception.LedgerApplicationException;
import com.carbonx.ledger.application.security.AuthenticatedUser;

final class PrincipalGuard {
    private PrincipalGuard() {}

    static AuthenticatedUser require(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw LedgerApplicationException.forbidden("Authentication required");
        }
        return principal;
    }
}
