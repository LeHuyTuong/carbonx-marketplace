package com.carbonx.identity.infrastructure.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for seeding the initial administrator on a clean database so an operator
 * can sign in immediately and provision CVA/Admin accounts without bypassing BR-08 self-register rules.
 */
@ConfigurationProperties(prefix = "carbonx.bootstrap.admin")
public record AdminBootstrapProperties(
        boolean enabled,
        String email,
        String password,
        String fullName
) {
}
