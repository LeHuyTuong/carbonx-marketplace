package com.carbonx.mrv.application.port.out;

import java.util.List;

/** Declarative Inter-Service Port (implemented via @FeignClient("identity-service") + Resilience4j CircuitBreaker). */
public interface IdentityServiceClientPort {
    String fetchApprovedCompanyName(Long companyId);
    default Long resolveCompanyIdByUserId(Long userId) {
        return userId;
    }
    List<String> verifyRegisteredPlates(Long companyId, List<String> plateNumbers);
}
