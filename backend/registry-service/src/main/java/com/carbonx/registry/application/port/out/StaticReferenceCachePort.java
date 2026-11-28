package com.carbonx.registry.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Shared Static Reference Data Cache Port (Redis) under Database-per-Service.
 * Caches read-mostly VM0038 emission factors and methodology constants locally to avoid
 * chatty cross-service REST calls into mrv-service during credit issuance & verification.
 */
public interface StaticReferenceCachePort {

    Optional<ProjectMethodologyRef> getProjectMethodology(Long projectId);

    void putProjectMethodology(ProjectMethodologyRef ref);

    record ProjectMethodologyRef(
            Long projectId,
            String projectTitle,
            BigDecimal emissionFactor,
            BigDecimal bufferRate,
            BigDecimal uncertaintyRate,
            BigDecimal leakageRate
    ) {}
}
