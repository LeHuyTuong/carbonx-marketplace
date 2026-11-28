package com.carbonx.mrv.infrastructure.client.dto;

import java.util.List;

/** Mirror of identity-service {@code POST /api/v1/internal/vehicles/verify-plates}. */
public record PlateVerificationRequest(Long companyId, List<String> plateNumbers) {
}
