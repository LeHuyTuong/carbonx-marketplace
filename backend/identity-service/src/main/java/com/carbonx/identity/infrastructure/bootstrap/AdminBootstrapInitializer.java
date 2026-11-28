package com.carbonx.identity.infrastructure.bootstrap;

import com.carbonx.identity.application.port.out.PasswordHasherPort;
import com.carbonx.identity.application.port.out.UserRepositoryPort;
import com.carbonx.identity.domain.user.Role;
import com.carbonx.identity.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** Seeds the initial ADMIN operator idempotently on an empty identity database. */
@Component
@ConditionalOnProperty(name = "carbonx.bootstrap.admin.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapInitializer implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.email() == null || properties.email().isBlank()
                || properties.password() == null || properties.password().isBlank()) {
            log.warn("Skipping initial ADMIN bootstrap because email or password is blank");
            return;
        }
        String normalizedEmail = properties.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            return;
        }
        String displayName = properties.fullName() == null || properties.fullName().isBlank()
                ? "CarbonX Administrator"
                : properties.fullName().trim();
        User admin = User.register(normalizedEmail, passwordHasher.encode(properties.password()),
                displayName, Role.ROLE_ADMIN);
        userRepository.save(admin);
        log.info("Bootstrapped initial ADMIN user {}", normalizedEmail);
    }
}
