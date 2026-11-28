package com.carbonx.mrv.infrastructure.client.dto;

import java.util.List;

/** Plates of the request that identity-service confirms as registered to the company. */
public record PlateVerificationResponse(Long companyId, List<String> verifiedPlates) {
}
