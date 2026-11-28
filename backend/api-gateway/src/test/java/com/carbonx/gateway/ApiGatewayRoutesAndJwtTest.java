package com.carbonx.gateway;

import com.carbonx.gateway.security.JwtAuthenticationGlobalFilter;
import com.carbonx.gateway.security.JwtGatewayProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ApiGatewayRoutesAndJwtTest {

    private static final String TEST_SECRET = "carbonx-gateway-test-only-signing-secret-32b";

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private JwtAuthenticationGlobalFilter jwtFilter;

    @Test
    void configuredRoutesCoverAllFrontendAndInternalDenyEndpoints() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        List<String> routeIds = routes.stream().map(Route::getId).toList();
        assertThat(routeIds).contains(
                "internal-deny",
                "identity-service",
                "mrv-service",
                "registry-service",
                "trading-service",
                "ledger-service",
                "notification-service"
        );

        String fullPredicates = routes.stream()
                .map(r -> r.getId() + "=" + r.getPredicate().toString())
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(fullPredicates).contains("/api/v1/internal/**");
        assertThat(fullPredicates).contains("/api/v1/applications/**");
        assertThat(fullPredicates).contains("/api/v1/holdings/**");
        assertThat(fullPredicates).contains("/api/v1/batches/**");
        assertThat(fullPredicates).contains("/api/v1/trading/**");
    }

    @Test
    void internalEndpointsAreBlockedWith403AtGateway() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/internal/holdings/5/lock")
                        .header("X-Internal-Service-Token", "someone-guessing-token")
                        .build());

        RecordingChain chain = new RecordingChain();
        jwtFilter.filter(exchange, chain).block();

        assertThat(chain.invoked()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void publicMarketplaceAndProjectGetsPassWithoutBearerTokenWhileMutationsRequire401() {
        MockServerWebExchange publicProjects = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/projects").build());
        RecordingChain projectsChain = new RecordingChain();
        jwtFilter.filter(publicProjects, projectsChain).block();
        assertThat(projectsChain.invoked()).isTrue();

        MockServerWebExchange publicListings = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/trading/listings").build());
        RecordingChain listingsChain = new RecordingChain();
        jwtFilter.filter(publicListings, listingsChain).block();
        assertThat(listingsChain.invoked()).isTrue();

        MockServerWebExchange publicFee = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/trading/settings/fee").build());
        RecordingChain feeChain = new RecordingChain();
        jwtFilter.filter(publicFee, feeChain).block();
        assertThat(feeChain.invoked()).isTrue();

        MockServerWebExchange feeMutation = MockServerWebExchange.from(
                MockServerHttpRequest.put("/api/v1/trading/settings/fee").build());
        RecordingChain mutationChain = new RecordingChain();
        jwtFilter.filter(feeMutation, mutationChain).block();
        assertThat(mutationChain.invoked()).isFalse();
        assertThat(feeMutation.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void missingOrInvalidBearerTokenReturns401OnProtectedRoutes() {
        MockServerWebExchange missingAuth = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/trading/orders").build());
        RecordingChain chain1 = new RecordingChain();
        jwtFilter.filter(missingAuth, chain1).block();
        assertThat(chain1.invoked()).isFalse();
        assertThat(missingAuth.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        MockServerWebExchange invalidAuth = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/trading/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt")
                        .build());
        RecordingChain chain2 = new RecordingChain();
        jwtFilter.filter(invalidAuth, chain2).block();
        assertThat(chain2.invoked()).isFalse();
        assertThat(invalidAuth.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void emptySigningSecretFailsClosedWith401InsteadOfBypassing() {
        JwtAuthenticationGlobalFilter unconfigured =
                new JwtAuthenticationGlobalFilter(new JwtGatewayProperties("", "carbonx-identity"));
        String signed = issueAccessToken(9L, 42L, "ROLE_COMPANY");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/trading/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + signed)
                        .build());

        RecordingChain chain = new RecordingChain();
        unconfigured.filter(exchange, chain).block();

        assertThat(chain.invoked()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validJwtStripsSpoofedHeadersAndForwardsVerifiedUserAndCompanyHeaders() {
        String token = issueAccessToken(9L, 42L, "ROLE_COMPANY");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/trading/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-User-Id", "1")
                        .header("X-Company-Id", "1")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .header("X-Internal-Service-Token", "spoofed-internal-secret")
                        .build());

        RecordingChain chain = new RecordingChain();
        jwtFilter.filter(exchange, chain).block();

        assertThat(chain.invoked()).isTrue();
        HttpHeaders downstream = chain.captured().getRequest().getHeaders();
        assertThat(downstream.getFirst("X-User-Id")).isEqualTo("9");
        assertThat(downstream.getFirst("X-Company-Id")).isEqualTo("42");
        assertThat(downstream.getFirst("X-User-Role")).isEqualTo("ROLE_COMPANY");
        assertThat(downstream.getFirst("X-User-Email")).isEqualTo("company@carbonx.io");
        assertThat(downstream.getFirst("X-Internal-Service-Token")).isNull();
        assertThat(downstream.getFirst("X-Request-Trace")).isNotBlank();
    }

    private static String issueAccessToken(long userId, long companyId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuer("carbonx-identity")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(300)))
                .claim("userId", userId)
                .claim("companyId", companyId)
                .claim("email", "company@carbonx.io")
                .claim("role", role)
                .claim("type", "access")
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private static final class RecordingChain implements GatewayFilterChain {
        private final AtomicReference<ServerWebExchange> seen = new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            seen.set(exchange);
            return Mono.empty();
        }

        boolean invoked() {
            return seen.get() != null;
        }

        ServerWebExchange captured() {
            return seen.get();
        }
    }
}
