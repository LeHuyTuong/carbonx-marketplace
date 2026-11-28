package com.carbonx.identity.application.port.in;

import java.time.Instant;
import java.util.List;

public interface IdentityQueryUseCase {
    CompanyView getCompanyById(Long companyId);
    default CompanyView getCompanyByUserId(Long userId) {
        return getCompanyById(userId);
    }
    List<CompanyView> listApprovedCompanies();
    List<CompanyView> listAllCompanies();
    EvKycView getMyEvKyc(Long evOwnerId);
    List<EvKycView> listAllEvKyc();
    UserSummaryView getUserProfile(Long userId);
    List<UserSummaryView> listAllUsersForAdmin();
    List<UserSummaryView> listCvas();
    List<VehicleView> listMyVehicles(Long evOwnerId);
    List<VehicleView> listVehiclesByPlates(Long companyId, List<String> plates);
    List<VehicleView> listAllVehiclesForAdmin(int page, int size);
    List<CompanyEvOwnerSummaryView> listCompanyEvOwnersSummary(Long companyUserId);

    record CompanyView(Long companyId, Long userId, String businessName, String taxCode, String businessLicense,
                       String address, String kycStatus, Instant createdAt) {
        public CompanyView(Long companyId, Long userId, String businessName, String taxCode, String kycStatus) {
            this(companyId, userId, businessName, taxCode, null, null, kycStatus, null);
        }
    }

    record VehicleView(Long id, Long evOwnerId, Long companyId, String plateNumber, String brand, String model,
                       String documentUrl, String status, Instant createdAt) {
        public VehicleView(Long id, Long evOwnerId, Long companyId, String plateNumber, String brand, String model,
                           String status) {
            this(id, evOwnerId, companyId, plateNumber, brand, model, null, status, null);
        }
    }

    record EvKycView(Long id, Long evOwnerId, String name, String email, String phone, String country,
                     String address, String birthDate, String documentType, String documentNumber,
                     String gender, String status, Instant createdAt) {}

    record UserSummaryView(Long id, String email, String fullName, String role, String status, Instant createdAt) {}

    record CompanyEvOwnerSummaryView(Long ownerId, String fullName, String email, int vehicleCount,
                                     List<VehicleView> vehicles) {}
}
