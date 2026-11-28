package com.carbonx.identity.infrastructure.security;

import com.carbonx.identity.application.port.out.PasswordHasherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** BCrypt-backed implementation of the password hashing port. */
@Component
@RequiredArgsConstructor
public class PasswordEncoderAdapter implements PasswordHasherPort {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean verify(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
