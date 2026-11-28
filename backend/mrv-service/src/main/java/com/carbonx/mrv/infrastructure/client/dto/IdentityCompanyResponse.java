package com.carbonx.mrv.infrastructure.client.dto;

/** Mirror of identity-service {@code GET /api/v1/internal/companies/{id}}. */
public record IdentityCompanyResponse(Long companyId, Long userId, String businessName, String taxCode,
                                      String kycStatus) {
}
