package com.carbonx.identity.application.port.out;

import com.carbonx.identity.domain.user.User;

/** Issues the stateless JWT pair (access + refresh) for an authenticated user. */
public interface TokenIssuerPort {

    IssuedTokens issue(User user);

    default IssuedTokens issue(User user, Long companyId) {
        return issue(user);
    }

    record IssuedTokens(String accessToken, String refreshToken) {
    }
}
