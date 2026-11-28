package com.carbonx.identity.infrastructure.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Registers the Google OIDC client from explicit properties instead of
 * {@code spring.security.oauth2.client.*}, so a missing credential disables the OAuth2 chain
 * instead of failing startup or shipping a default client id.
 */
@Configuration
@EnableConfigurationProperties(OAuth2Properties.class)
public class GoogleOAuth2ClientConfig {

    static final String CLIENT_ID_PROPERTY = "carbonx.security.oauth2.google.client-id";
    static final String CLIENT_SECRET_PROPERTY = "carbonx.security.oauth2.google.client-secret";

    @Bean
    @Conditional(GoogleCredentialsConfigured.class)
    ClientRegistrationRepository clientRegistrationRepository(OAuth2Properties properties) {
        OAuth2Properties.Google google = properties.google();
        ClientRegistration registration = CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId(google.clientId())
                .clientSecret(google.clientSecret())
                .redirectUri(google.redirectUri())
                .scope("openid", "profile", "email")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    /** Google login is enabled only when both a client id and a client secret exist. */
    static final class GoogleCredentialsConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            return hasText(environment.getProperty(CLIENT_ID_PROPERTY))
                    && hasText(environment.getProperty(CLIENT_SECRET_PROPERTY));
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
