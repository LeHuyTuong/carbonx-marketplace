package com.carbonx.identity.api;

import java.util.List;
import java.util.Optional;

/** Public Query Port exposed by Identity Bounded Context to other modules (no repository leak). */
public interface IdentityQueryPort {
    Optional<CompanySnapshot> findCompanyById(Long companyId);

    List<VehicleOwnerSnapshot> findVehiclesByPlates(Long companyId, List<String> plateNumbers);

    record CompanySnapshot(Long companyId, Long userId, String businessName, String kycStatus) {}

    record VehicleOwnerSnapshot(Long vehicleId, String plateNumber, Long evOwnerId, String ownerEmail) {}
}
