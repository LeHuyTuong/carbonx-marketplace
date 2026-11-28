package com.carbonx.identity.infrastructure.security;

import com.carbonx.identity.application.port.in.IdentityCommandUseCase;
import com.carbonx.identity.application.port.in.IdentityCommandUseCase.AuthResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GoogleOAuth2SuccessHandlerTest {

    private static final String REDIRECT_BASE = "https://app.carbonx.vn/oauth-success";

    private final IdentityCommandUseCase commandUseCase = mock(IdentityCommandUseCase.class);
    private final OAuth2Properties properties = new OAuth2Properties(REDIRECT_BASE,
            new OAuth2Properties.Google("client-id", "client-secret", "{baseUrl}/login/oauth2/code/google"));
    private final GoogleOAuth2SuccessHandler handler = new GoogleOAuth2SuccessHandler(commandUseCase, properties);

    @Test
    @DisplayName("a successful Google login redirects to the SPA with #token= and hides the refresh token")
    void redirectsWithTokenInFragment() throws Exception {
        given(commandUseCase.loginWithGoogleOAuth2("owner@gmail.com", "google-sub-1", "Nguyen Van A"))
                .willReturn(new AuthResult("access-token-value", "refresh-token-value", 11L,
                        "owner@gmail.com", "ROLE_EV_OWNER"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                googleAuthentication(Map.of("sub", "google-sub-1", "email", "owner@gmail.com",
                        "name", "Nguyen Van A")));

        String location = response.getRedirectedUrl();
        assertThat(location).isEqualTo(REDIRECT_BASE + "#token=access-token-value");
        assertThat(location).doesNotContain("?");
        assertThat(location).doesNotContain("refresh-token-value");
    }

    @Test
    @DisplayName("a Google profile without an email is rejected with 401 and no token is issued")
    void rejectsProfileWithoutEmail() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                googleAuthentication(Map.of("sub", "google-sub-2")));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getRedirectedUrl()).isNull();
        verifyNoInteractions(commandUseCase);
    }

    private static OAuth2AuthenticationToken googleAuthentication(Map<String, Object> claims) {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("google-id-token", now, now.plusSeconds(600), claims);
        OidcUser oidcUser = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_EV_OWNER")), idToken);
        return new OAuth2AuthenticationToken(oidcUser, List.of(new SimpleGrantedAuthority("ROLE_EV_OWNER")),
                "google");
    }
}
