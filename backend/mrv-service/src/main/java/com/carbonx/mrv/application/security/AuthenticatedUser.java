package com.carbonx.mrv.application.security;

/**
 * Verified JWT principal handed to the API layer. Kept in the application ring so REST
 * controllers never depend on the infrastructure security filter or on Spring Security types.
 */
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
