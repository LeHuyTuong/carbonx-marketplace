package com.carbonx.identity.domain.user;

/** Account lifecycle state persisted in {@code users.status}. */
public enum UserStatus {
    ACTIVE,
    PENDING_VERIFICATION,
    SUSPENDED
}
