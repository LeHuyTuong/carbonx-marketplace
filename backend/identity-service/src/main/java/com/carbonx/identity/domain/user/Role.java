package com.carbonx.identity.domain.user;

import com.carbonx.identity.domain.shared.DomainValidationException;

import java.util.Locale;

/** The four CarbonX security roles; the enum name is exactly the Spring Security authority. */
public enum Role {
    ROLE_EV_OWNER,
    ROLE_COMPANY,
    ROLE_CVA,
    ROLE_ADMIN;

    public static Role from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainValidationException("Role is required");
        }
        String candidate = raw.trim().toUpperCase(Locale.ROOT);
        if (!candidate.startsWith("ROLE_")) {
            candidate = "ROLE_" + candidate;
        }
        if ("ROLE_CC_BUYER".equals(candidate) || "ROLE_BUYER".equals(candidate)) {
            return ROLE_COMPANY;
        }
        for (Role role : values()) {
            if (role.name().equals(candidate)) {
                return role;
            }
        }
        throw new DomainValidationException("Unknown role: " + raw);
    }

    /** Only EV owners and companies may open their own account; CVA/ADMIN are provisioned internally. */
    public boolean selfRegistrable() {
        return this == ROLE_EV_OWNER || this == ROLE_COMPANY;
    }
}
