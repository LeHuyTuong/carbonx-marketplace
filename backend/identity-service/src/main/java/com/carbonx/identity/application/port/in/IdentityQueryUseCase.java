package com.carbonx.identity.application.port.in;

import java.util.List;

public interface IdentityQueryUseCase {
    CompanyView getCompanyById(Long companyId);
    List<CompanyView> listApprovedCompanies();
    List<VehicleView> listMyVehicles(Long evOwnerId);
    List<VehicleView> listVehiclesByPlates(Long companyId, List<String> plates);
    List<VehicleView> listAllVehiclesForAdmin(int page, int size);

    record CompanyView(Long companyId, Long userId, String businessName, String taxCode, String kycStatus) {}
    record VehicleView(Long id, Long evOwnerId, Long companyId, String plateNumber, String brand, String model, String status) {}
}
