package com.carbonx.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Edge security filter for Spring Cloud Gateway:
 * 1. Always rejects {@code /api/v1/internal/**} from external clients with 403 Forbidden.
 * 2. Strips any spoofed identity / internal-token headers coming from the outside.
 * 3. Validates Bearer JWT on protected routes (fail-closed if signing key < 32 bytes) and
 *    forwards verified {@code X-User-Id}, {@code X-Company-Id}, {@code X-User-Role}, {@code X-User-Email},
 *    and {@code X-Request-Trace}.
 */
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationGlobalFilter.class);

    static final String HEADER_USER_ID = "X-User-Id";
    static final String HEADER_COMPANY_ID = "X-Company-Id";
    static final String HEADER_USER_ROLE = "X-User-Role";
    static final String HEADER_USER_EMAIL = "X-User-Email";
    static final String HEADER_REQUEST_TRACE = "X-Request-Trace";
    static final String HEADER_INTERNAL_TOKEN = "X-Internal-Service-Token";

    private static final int MIN_SECRET_BYTES = 32;
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TYPE_ACCESS = "access";
    private static final Set<String> KNOWN_ROLES =
            Set.of("ROLE_EV_OWNER", "ROLE_COMPANY", "ROLE_CVA", "ROLE_ADMIN");
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/**",
            "/oauth2/**",
            "/login/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/webjars/**"
    );

    private final JwtGatewayProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private volatile SecretKey signingKey;

    public JwtAuthenticationGlobalFilter(JwtGatewayProperties properties) {
        this.properties = properties;
        initSigningKey();
    }

    @PostConstruct
    void initSigningKey() {
        String rawSecret = properties.secret();
        if (rawSecret == null || rawSecret.isBlank()) {
            this.signingKey = null;
            log.warn("Gateway JWT signing secret is empty — protected routes will fail closed with 401");
            return;
        }
        byte[] keyBytes = rawSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            this.signingKey = null;
            log.error("Gateway JWT secret shorter than {} bytes ({} bytes) — protected routes will fail closed with 401",
                    MIN_SECRET_BYTES, keyBytes.length);
            return;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String normalizedPath = normalizePath(request.getURI().getRawPath());
        if (normalizedPath == null || pathMatcher.match("/api/v1/internal/**", normalizedPath)) {
            return writeError(exchange.getResponse(), HttpStatus.FORBIDDEN,
                    "Internal service endpoints are not exposed through API gateway");
        }

        String traceId = resolveTraceId(request);
        if (isPublicRequest(request.getMethod(), normalizedPath)) {
            ServerHttpRequest sanitized = stripSpoofedHeaders(request.mutate(), traceId).build();
            return chain.filter(exchange.mutate().request(sanitized).build());
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return writeError(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "Missing Bearer access token");
        }
        String rawToken = authHeader.substring(BEARER_PREFIX.length()).trim();
        VerifiedClaims claims = verifyToken(rawToken);
        if (claims == null) {
            return writeError(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "Invalid or expired access token");
        }

        ServerHttpRequest.Builder mutated = stripSpoofedHeaders(request.mutate(), traceId)
                .header(HEADER_USER_ID, String.valueOf(claims.userId()))
                .header(HEADER_COMPANY_ID, String.valueOf(claims.companyId()))
                .header(HEADER_USER_ROLE, claims.role())
                .header(HEADER_USER_EMAIL, claims.email());

        return chain.filter(exchange.mutate().request(mutated.build()).build());
    }

    private VerifiedClaims verifyToken(String rawToken) {
        SecretKey key = this.signingKey;
        if (key == null || rawToken.isBlank()) {
            return null;
        }
        try {
            var parser = Jwts.parserBuilder().setSigningKey(key);
            if (properties.issuer() != null && !properties.issuer().isBlank()) {
                parser.requireIssuer(properties.issuer());
            }
            Claims claims = parser.build().parseClaimsJws(rawToken).getBody();
            if (!TYPE_ACCESS.equals(claims.get("type", String.class))) {
                return null;
            }
            Object rawUserId = claims.get("userId");
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);
            if (!(rawUserId instanceof Number uid) || email == null || email.isBlank()
                    || role == null || !KNOWN_ROLES.contains(role)) {
                return null;
            }
            Object rawCompanyId = claims.get("companyId");
            long userId = uid.longValue();
            long companyId = rawCompanyId instanceof Number cid ? cid.longValue() : userId;
            return new VerifiedClaims(userId, companyId, email, role);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Gateway rejected bearer token: {}", ex.getMessage());
            return null;
        }
    }

    private boolean isPublicRequest(HttpMethod method, String path) {
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && (
                path.equals("/api/v1/projects") || path.startsWith("/api/v1/projects/")
                        || path.equals("/api/v1/marketplace") || path.startsWith("/api/v1/marketplace/")
                        || path.equals("/api/v1/trading/listings") || path.startsWith("/api/v1/trading/listings/")
                        || path.equals("/api/v1/trading/settings/fee"))) {
            return true;
        }
        for (String pattern : PUBLIC_PATHS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private static ServerHttpRequest.Builder stripSpoofedHeaders(ServerHttpRequest.Builder builder, String traceId) {
        return builder.headers(headers -> {
            headers.remove(HEADER_USER_ID);
            headers.remove(HEADER_COMPANY_ID);
            headers.remove(HEADER_USER_ROLE);
            headers.remove(HEADER_USER_EMAIL);
            headers.remove(HEADER_INTERNAL_TOKEN);
            headers.set(HEADER_REQUEST_TRACE, traceId);
        });
    }

    private static String resolveTraceId(ServerHttpRequest request) {
        String existing = request.getHeaders().getFirst(HEADER_REQUEST_TRACE);
        return (existing != null && !existing.isBlank()) ? existing : UUID.randomUUID().toString();
    }

    private static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "/";
        }
        if (rawPath.contains("..")) {
            return null;
        }
        try {
            return URI.create(rawPath).normalize().getPath();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Mono<Void> writeError(ServerHttpResponse response, HttpStatus status, String message) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":" + status.value() + ",\"error\":\"" + status.name()
                + "\",\"message\":\"" + message.replace("\"", "'") + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private record VerifiedClaims(long userId, long companyId, String email, String role) {
    }
}
