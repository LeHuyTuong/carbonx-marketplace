package com.carbonx.ledger.application.security;

public record AuthenticatedUser(Long userId, Long companyId, String email, String role) {

    public AuthenticatedUser(Long userId, String email, String role) {
        this(userId, null, email, role);
    }

    public Long effectiveCompanyId() {
        return companyId != null ? companyId : userId;
    }

    public boolean hasRole(String expected) {
        return role != null && role.equals(expected);
    }
}
