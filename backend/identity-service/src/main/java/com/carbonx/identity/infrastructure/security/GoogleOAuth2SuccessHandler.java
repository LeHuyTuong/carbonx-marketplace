package com.carbonx.identity.infrastructure.security;

import com.carbonx.identity.application.port.in.IdentityCommandUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Completes the Google login by exchanging the OIDC identity for a CarbonX JWT and redirecting
 * to the SPA with {@code #token=...} in the URL fragment, so the token never reaches a server
 * access log, a Referer header or the browser history query string.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final IdentityCommandUseCase identityCommandUseCase;
    private final OAuth2Properties oauth2Properties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        GoogleProfile profile = GoogleProfile.from(authentication);
        if (profile == null) {
            log.warn("Google OAuth2 login rejected: the provider did not expose an email address");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Google account did not expose an email address");
            return;
        }
        IdentityCommandUseCase.AuthResult result =
                identityCommandUseCase.loginWithGoogleOAuth2(profile.email(), profile.subject(), profile.fullName());
        response.sendRedirect(oauth2Properties.successRedirectUri() + "#token=" + result.accessToken());
    }

    /** Normalises the two principal shapes Spring can hand back for a Google login. */
    private record GoogleProfile(String email, String subject, String fullName) {

        static GoogleProfile from(Authentication authentication) {
            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }
            Object principal = authentication.getPrincipal();
            if (principal instanceof OidcUser oidcUser) {
                String email = oidcUser.getEmail();
                if (email == null || email.isBlank()) {
                    return null;
                }
                return new GoogleProfile(email, oidcUser.getSubject(), oidcUser.getFullName());
            }
            if (principal instanceof OAuth2User oauth2User) {
                Map<String, Object> attributes = oauth2User.getAttributes();
                String email = asText(attributes.get("email"));
                if (email == null) {
                    return null;
                }
                return new GoogleProfile(email, asText(attributes.get("sub")), asText(attributes.get("name")));
            }
            return null;
        }

        private static String asText(Object value) {
            if (value == null) {
                return null;
            }
            String text = String.valueOf(value);
            return text.isBlank() ? null : text;
        }
    }
}
