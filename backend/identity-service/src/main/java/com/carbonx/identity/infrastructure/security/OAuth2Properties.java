package com.carbonx.identity.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google OIDC client settings plus the SPA landing page that receives the JWT in the URL
 * fragment. Credentials have no defaults — the OAuth2 login chain is only wired when both
 * values are supplied through the environment.
 */
@ConfigurationProperties(prefix = "carbonx.security.oauth2")
public record OAuth2Properties(String successRedirectUri, Google google) {

    public OAuth2Properties {
        successRedirectUri = successRedirectUri == null || successRedirectUri.isBlank()
                ? "http://localhost:5173/oauth-success"
                : successRedirectUri.trim();
        google = google == null ? new Google(null, null, null) : google;
    }

    public record Google(String clientId, String clientSecret, String redirectUri) {

        public Google {
            redirectUri = redirectUri == null || redirectUri.isBlank()
                    ? "{baseUrl}/login/oauth2/code/{registrationId}"
                    : redirectUri.trim();
        }

        public boolean configured() {
            return clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank();
        }
    }
}
