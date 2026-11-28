package com.carbonx.identity.application.port.out;

/** Hashing port so the application layer never sees BCrypt directly. */
public interface PasswordHasherPort {
    String encode(String rawPassword);

    boolean verify(String rawPassword, String passwordHash);
}
