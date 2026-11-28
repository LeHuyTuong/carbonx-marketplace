package com.carbonx.identity.infrastructure.security;

import com.carbonx.identity.application.security.AuthenticatedUser;
import com.carbonx.identity.domain.shared.DomainValidationException;
import com.carbonx.identity.domain.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Validates signature, issuer, expiry and token type, then rebuilds the principal from the
 * {@code userId}/{@code email}/{@code role} claims. Unknown roles are rejected so a forged or
 * foreign token can never widen the RBAC surface.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenVerifier {

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

            if (!JwtTokenIssuer.TYPE_ACCESS.equals(claims.get(JwtTokenIssuer.CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            Object userId = claims.get(JwtTokenIssuer.CLAIM_USER_ID);
            Object companyIdClaim = claims.get(JwtTokenIssuer.CLAIM_COMPANY_ID);
            Long companyId = companyIdClaim instanceof Number companyNum ? companyNum.longValue() : null;
            String email = claims.get(JwtTokenIssuer.CLAIM_EMAIL, String.class);
            String role = claims.get(JwtTokenIssuer.CLAIM_ROLE, String.class);
            if (!(userId instanceof Number number) || email == null || role == null) {
                return Optional.empty();
            }
            Role.from(role);
            return Optional.of(new AuthenticatedUser(number.longValue(), companyId, email, role));
        } catch (JwtException | IllegalArgumentException | DomainValidationException ex) {
            // Never log the token value itself.
            log.debug("Rejected bearer token: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
