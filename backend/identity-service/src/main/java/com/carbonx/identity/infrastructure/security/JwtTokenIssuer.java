package com.carbonx.identity.infrastructure.security;

import com.carbonx.identity.application.port.out.TokenIssuerPort;
import com.carbonx.identity.domain.user.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Issues HS256 access/refresh JWTs carrying {@code userId}, {@code email} and {@code role}. */
@Component
@RequiredArgsConstructor
public class JwtTokenIssuer implements TokenIssuerPort {

    static final String CLAIM_USER_ID = "userId";
    static final String CLAIM_COMPANY_ID = "companyId";
    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_ROLE = "role";
    static final String CLAIM_TYPE = "type";
    static final String TYPE_ACCESS = "access";
    static final String TYPE_REFRESH = "refresh";

    private final JwtSigningKeyHolder signingKey;
    private final JwtProperties properties;

    @Override
    public IssuedTokens issue(User user) {
        return issue(user, null);
    }

    @Override
    public IssuedTokens issue(User user, Long companyId) {
        Instant now = Instant.now();
        var accessBuilder = Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setIssuer(properties.issuer())
                .setSubject(String.valueOf(user.id()))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(properties.accessTokenTtl())))
                .claim(CLAIM_USER_ID, user.id())
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_ROLE, user.role().name())
                .claim(CLAIM_TYPE, TYPE_ACCESS);
        if (companyId != null) {
            accessBuilder.claim(CLAIM_COMPANY_ID, companyId);
        }
        String accessToken = accessBuilder
                .signWith(signingKey.key(), SignatureAlgorithm.HS256)
                .compact();

        String refreshToken = Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setIssuer(properties.issuer())
                .setSubject(String.valueOf(user.id()))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(properties.refreshTokenTtl())))
                .claim(CLAIM_USER_ID, user.id())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .signWith(signingKey.key(), SignatureAlgorithm.HS256)
                .compact();

        return new IssuedTokens(accessToken, refreshToken);
    }
}
