package com.carbonx.identity.domain.user;

import com.carbonx.identity.domain.shared.DomainValidationException;

import java.time.Instant;
import java.util.Locale;

/**
 * User aggregate root: the single source of truth for credentials, role and account state.
 * Immutable — every state change returns a new instance so the aggregate can never be half-updated.
 */
public final class User {

    private static final int EMAIL_MAX_LENGTH = 191;

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final Role role;
    private final String oauthSubject;
    private final UserStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private User(Long id, String email, String passwordHash, String fullName, Role role,
                 String oauthSubject, UserStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.oauthSubject = oauthSubject;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Self-service registration. The account becomes ACTIVE immediately; OTP verification stays available. */
    public static User register(String email, String passwordHash, String fullName, Role role) {
        return new User(null, normalizeEmail(email), requireText(passwordHash, "passwordHash"),
                trimToNull(fullName), requireRole(role), null, UserStatus.ACTIVE, null, null);
    }

    /** Account created from a Google/OpenID Connect identity — no usable local password. */
    public static User externalIdentity(String email, String unusablePasswordHash, String fullName, String oauthSubject) {
        return new User(null, normalizeEmail(email), requireText(unusablePasswordHash, "passwordHash"),
                trimToNull(fullName), Role.ROLE_EV_OWNER, requireText(oauthSubject, "oauthSubject"),
                UserStatus.ACTIVE, null, null);
    }

    /** Rehydrates a persisted row. */
    public static User rehydrate(Long id, String email, String passwordHash, String fullName, Role role,
                                 String oauthSubject, UserStatus status, Instant createdAt, Instant updatedAt) {
        return new User(id, normalizeEmail(email), requireText(passwordHash, "passwordHash"),
                trimToNull(fullName), requireRole(role), trimToNull(oauthSubject), requireStatus(status),
                createdAt, updatedAt);
    }

    /** Persistence adapters attach the database generated identifier. */
    public User withId(Long assignedId) {
        return new User(assignedId, email, passwordHash, fullName, role, oauthSubject, status, createdAt, updatedAt);
    }

    public User linkOauthSubject(String subject) {
        return new User(id, email, passwordHash, fullName, role, requireText(subject, "oauthSubject"),
                status, createdAt, updatedAt);
    }

    public User activate() {
        return new User(id, email, passwordHash, fullName, role, oauthSubject, UserStatus.ACTIVE, createdAt, updatedAt);
    }

    public User withStatus(UserStatus nextStatus) {
        if (nextStatus == null) {
            throw new DomainValidationException("status is required");
        }
        return new User(id, email, passwordHash, fullName, role, oauthSubject, nextStatus, createdAt, updatedAt);
    }

    public User withPasswordHash(String nextHash) {
        return new User(id, email, requireText(nextHash, "passwordHash"), fullName, role, oauthSubject,
                status, createdAt, updatedAt);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean is(Role expected) {
        return role == expected;
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String fullName() {
        return fullName;
    }

    public Role role() {
        return role;
    }

    public String oauthSubject() {
        return oauthSubject;
    }

    public UserStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static String normalizeEmail(String email) {
        String value = requireText(email, "email").toLowerCase(Locale.ROOT);
        int at = value.indexOf('@');
        if (at <= 0 || at == value.length() - 1 || value.chars().anyMatch(Character::isWhitespace)) {
            throw new DomainValidationException("Invalid email address");
        }
        if (value.length() > EMAIL_MAX_LENGTH) {
            throw new DomainValidationException("Email must not exceed " + EMAIL_MAX_LENGTH + " characters");
        }
        return value;
    }

    private static Role requireRole(Role role) {
        if (role == null) {
            throw new DomainValidationException("Role is required");
        }
        return role;
    }

    private static UserStatus requireStatus(UserStatus status) {
        if (status == null) {
            throw new DomainValidationException("Status is required");
        }
        return status;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field + " is required");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
