package com.carbonx.identity.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password encoder lives outside {@link SecurityConfig} on purpose: the command service reaches
 * the encoder through its persistence port, and keeping it here avoids a bean cycle between the
 * security filter chain, the OAuth2 success handler and the command service.
 */
@Configuration
public class PasswordEncodingConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
