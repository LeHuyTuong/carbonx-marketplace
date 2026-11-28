package com.carbonx.identity.infrastructure.security;

import com.carbonx.identity.application.port.out.TokenIssuerPort.IssuedTokens;
import com.carbonx.identity.application.security.AuthenticatedUser;
import com.carbonx.identity.domain.user.Role;
import com.carbonx.identity.domain.user.User;
import com.carbonx.identity.domain.user.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenTest {

    private static final String SECRET = "carbonx-identity-unit-test-signing-secret-32b";
    private static final String OTHER_SECRET = "carbonx-identity-unit-test-other-secret-32bb";

    private final JwtProperties properties = new JwtProperties(SECRET, "carbonx-identity-test",
            Duration.ofHours(1), Duration.ofDays(7));
    private final JwtSigningKeyHolder keyHolder = new JwtSigningKeyHolder(properties);
    private final JwtTokenIssuer issuer = new JwtTokenIssuer(keyHolder, properties);
    private final JwtTokenVerifier verifier = new JwtTokenVerifier(keyHolder, properties);

    @Test
    @DisplayName("the access token carries userId, email and role and verifies back to the principal")
    void accessTokenCarriesRequiredClaims() {
        IssuedTokens tokens = issuer.issue(activeOwner());

        Claims claims = Jwts.parserBuilder()
                .requireIssuer("carbonx-identity-test")
                .setSigningKey(keyHolder.key())
                .build()
                .parseClaimsJws(tokens.accessToken())
                .getBody();

        assertThat(((Number) claims.get("userId")).longValue()).isEqualTo(7L);
        assertThat(claims.get("email", String.class)).isEqualTo("owner@carbonx.vn");
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_EV_OWNER");

        Optional<AuthenticatedUser> principal = verifier.verify(tokens.accessToken());
        assertThat(principal).isPresent();
        assertThat(principal.get().userId()).isEqualTo(7L);
        assertThat(principal.get().companyId()).isNull();
        assertThat(principal.get().email()).isEqualTo("owner@carbonx.vn");
        assertThat(principal.get().hasRole("ROLE_EV_OWNER")).isTrue();
    }

    @Test
    @DisplayName("a company access token preserves both userId and companies.id as distinct claims")
    void companyTokenPreservesCompanyIdClaim() {
        User companyUser = User.rehydrate(8L, "company@carbonx.vn", "bcrypt-hash", "Green Fleet",
                Role.ROLE_COMPANY, null, UserStatus.ACTIVE, null, null);
        IssuedTokens tokens = issuer.issue(companyUser, 3L);

        Optional<AuthenticatedUser> principal = verifier.verify(tokens.accessToken());
        assertThat(principal).isPresent();
        assertThat(principal.get().userId()).isEqualTo(8L);
        assertThat(principal.get().companyId()).isEqualTo(3L);
        assertThat(principal.get().effectiveCompanyId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("the refresh token is not accepted as an access token")
    void refreshTokenIsNotAnAccessToken() {
        IssuedTokens tokens = issuer.issue(activeOwner());

        assertThat(tokens.refreshToken()).isNotEqualTo(tokens.accessToken());
        assertThat(verifier.verify(tokens.refreshToken())).isEmpty();
    }

    @Test
    @DisplayName("a token signed with another secret is rejected")
    void foreignSignatureIsRejected() {
        JwtSigningKeyHolder otherKeys = new JwtSigningKeyHolder(
                new JwtProperties(OTHER_SECRET, "carbonx-identity-test", Duration.ofHours(1), Duration.ofDays(7)));
        IssuedTokens forged = new JwtTokenIssuer(otherKeys,
                new JwtProperties(OTHER_SECRET, "carbonx-identity-test", Duration.ofHours(1), Duration.ofDays(7)))
                .issue(activeOwner());

        assertThat(verifier.verify(forged.accessToken())).isEmpty();
    }

    @Test
    @DisplayName("an expired access token is rejected")
    void expiredTokenIsRejected() {
        JwtProperties expiredProperties = new JwtProperties(SECRET, "carbonx-identity-test",
                Duration.ofSeconds(-30), Duration.ofDays(7));
        IssuedTokens expired = new JwtTokenIssuer(keyHolder, expiredProperties).issue(activeOwner());

        assertThat(verifier.verify(expired.accessToken())).isEmpty();
    }

    @Test
    @DisplayName("a token from another issuer is rejected")
    void foreignIssuerIsRejected() {
        JwtProperties otherIssuer = new JwtProperties(SECRET, "evil-issuer", Duration.ofHours(1), Duration.ofDays(7));
        IssuedTokens foreign = new JwtTokenIssuer(keyHolder, otherIssuer).issue(activeOwner());

        assertThat(verifier.verify(foreign.accessToken())).isEmpty();
    }

    @Test
    @DisplayName("blank or malformed tokens are rejected without throwing")
    void malformedTokensAreRejected() {
        assertThat(verifier.verify(null)).isEmpty();
        assertThat(verifier.verify("")).isEmpty();
        assertThat(verifier.verify("not-a-jwt")).isEmpty();
    }

    @Test
    @DisplayName("a signing secret shorter than 32 bytes fails startup")
    void shortSecretFailsFast() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> new JwtSigningKeyHolder(
                        new JwtProperties("too-short", "carbonx-identity-test", Duration.ofHours(1), Duration.ofDays(7))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    private static User activeOwner() {
        return User.rehydrate(7L, "owner@carbonx.vn", "bcrypt-hash", "Nguyen Van A",
                Role.ROLE_EV_OWNER, null, UserStatus.ACTIVE, null, null);
    }
}
