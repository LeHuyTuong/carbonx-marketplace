package com.carbonx.registry.infrastructure.security;

import com.carbonx.registry.application.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Validates signature, issuer, expiry and token type, then rebuilds the principal from the
 * {@code userId}/{@code email}/{@code role} claims issued by identity-service. Unknown roles are
 * rejected so a forged or foreign token can never widen the RBAC surface.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenVerifier {

    static final String CLAIM_USER_ID = "userId";
    static final String CLAIM_COMPANY_ID = "companyId";
    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_ROLE = "role";
    static final String CLAIM_TYPE = "type";
    static final String TYPE_ACCESS = "access";

    private static final Set<String> KNOWN_ROLES =
            Set.of("ROLE_EV_OWNER", "ROLE_COMPANY", "ROLE_CVA", "ROLE_ADMIN");

    private final JwtSigningKeyHolder signingKey;
    private final JwtProperties properties;

    public Optional<AuthenticatedUser> verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parserBuilder()
                    .requireIssuer(properties.issuer())
                    .setSigningKey(signingKey.key())
                    .build()
                    .parseClaimsJws(rawToken)
                    .getBody();

            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            Object userId = claims.get(CLAIM_USER_ID);
            Object companyIdClaim = claims.get(CLAIM_COMPANY_ID);
            Long companyId = companyIdClaim instanceof Number companyNum ? companyNum.longValue() : null;
            String email = claims.get(CLAIM_EMAIL, String.class);
            String role = claims.get(CLAIM_ROLE, String.class);
            if (!(userId instanceof Number number) || email == null || role == null || !KNOWN_ROLES.contains(role)) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(number.longValue(), companyId, email, role));
        } catch (JwtException | IllegalArgumentException ex) {
            // Never log the token value itself.
            log.debug("Rejected bearer token: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
