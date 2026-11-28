package com.carbonx.identity.application.service;

import com.carbonx.identity.application.exception.IdentityApplicationException;
import com.carbonx.identity.application.port.in.IdentityQueryUseCase;
import com.carbonx.identity.application.port.out.CompanyRepositoryPort;
import com.carbonx.identity.application.port.out.KycProfileRepositoryPort;
import com.carbonx.identity.application.port.out.UserRepositoryPort;
import com.carbonx.identity.application.port.out.VehicleRepositoryPort;
import com.carbonx.identity.domain.company.Company;
import com.carbonx.identity.domain.kyc.KycProfile;
import com.carbonx.identity.domain.user.Role;
import com.carbonx.identity.domain.user.User;
import com.carbonx.identity.domain.vehicle.PlateNumber;
import com.carbonx.identity.domain.vehicle.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read side of the identity CQRS split — no state changes, read-only transactions. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IdentityQueryService implements IdentityQueryUseCase {

    private static final int MAX_PAGE_SIZE = 100;

    private final CompanyRepositoryPort companyRepository;
    private final VehicleRepositoryPort vehicleRepository;
    private final UserRepositoryPort userRepository;
    private final KycProfileRepositoryPort kycProfileRepository;

    @Override
    public CompanyView getCompanyById(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> IdentityApplicationException.notFound("Company", companyId));
        return toCompanyView(company);
    }

    @Override
    public CompanyView getCompanyByUserId(Long userId) {
        Company company = companyRepository.findByUserId(userId)
                .orElseThrow(() -> IdentityApplicationException.notFound("Company for user", userId));
        return toCompanyView(company);
    }

    @Override
    public List<CompanyView> listApprovedCompanies() {
        return companyRepository.findAllApproved().stream()
                .map(IdentityQueryService::toCompanyView)
                .toList();
    }

    @Override
    public List<CompanyView> listAllCompanies() {
        return companyRepository.findAllOrdered().stream()
                .map(IdentityQueryService::toCompanyView)
                .toList();
    }

    @Override
    public EvKycView getMyEvKyc(Long evOwnerId) {
        KycProfile profile = kycProfileRepository.findByEvOwnerId(evOwnerId)
                .orElseThrow(() -> IdentityApplicationException.notFound("EV KYC profile for user", evOwnerId));
        User user = userRepository.findById(evOwnerId).orElse(null);
        return toEvKycView(profile, user);
    }

    @Override
    public List<EvKycView> listAllEvKyc() {
        return kycProfileRepository.findAllOrdered().stream()
                .map(profile -> toEvKycView(profile, userRepository.findById(profile.evOwnerId()).orElse(null)))
                .toList();
    }

    @Override
    public UserSummaryView getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> IdentityApplicationException.notFound("User", userId));
        return toUserSummaryView(user);
    }

    @Override
    public List<UserSummaryView> listAllUsersForAdmin() {
        return userRepository.findAllOrdered().stream()
                .map(IdentityQueryService::toUserSummaryView)
                .toList();
    }

    @Override
    public List<UserSummaryView> listCvas() {
        return userRepository.findByRole(Role.ROLE_CVA).stream()
                .map(IdentityQueryService::toUserSummaryView)
                .toList();
    }

    @Override
    public List<CompanyEvOwnerSummaryView> listCompanyEvOwnersSummary(Long companyUserId) {
        Company company = companyRepository.findByUserId(companyUserId)
                .orElseThrow(() -> IdentityApplicationException.notFound("Company for user", companyUserId));
        List<Vehicle> companyVehicles = vehicleRepository.findByCompanyId(company.id());
        Map<Long, List<VehicleView>> grouped = new LinkedHashMap<>();
        for (Vehicle vehicle : companyVehicles) {
            grouped.computeIfAbsent(vehicle.evOwnerId(), ignored -> new ArrayList<>())
                    .add(toVehicleView(vehicle));
        }
        return grouped.entrySet().stream()
                .map(entry -> {
                    User owner = userRepository.findById(entry.getKey()).orElse(null);
                    String fullName = owner == null || owner.fullName() == null
                            ? "EV Owner #" + entry.getKey()
                            : owner.fullName();
                    String email = owner == null ? "" : owner.email();
                    return new CompanyEvOwnerSummaryView(entry.getKey(), fullName, email,
                            entry.getValue().size(), entry.getValue());
                })
                .toList();
    }

    @Override
    public List<VehicleView> listMyVehicles(Long evOwnerId) {
        return vehicleRepository.findByEvOwnerId(evOwnerId).stream()
                .map(IdentityQueryService::toVehicleView)
                .toList();
    }

    @Override
    public List<VehicleView> listVehiclesByPlates(Long companyId, List<String> plates) {
        if (plates == null || plates.isEmpty()) {
            return List.of();
        }
        List<String> normalized = plates.stream()
                .map(plate -> new PlateNumber(plate).value())
                .distinct()
                .toList();
        return mapVehicles(vehicleRepository.findByCompanyIdAndPlateNumbers(companyId, normalized));
    }

    @Override
    public List<VehicleView> listAllVehiclesForAdmin(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return mapVehicles(vehicleRepository.findAll(safePage, safeSize));
    }

    private List<VehicleView> mapVehicles(Collection<Vehicle> vehicles) {
        return vehicles.stream()
                .map(IdentityQueryService::toVehicleView)
                .toList();
    }

    private static CompanyView toCompanyView(Company company) {
        return new CompanyView(company.id(), company.userId(), company.businessName(),
                company.taxCode(), company.businessLicense(), company.address(),
                company.kycStatus().name(), company.createdAt());
    }

    private static VehicleView toVehicleView(Vehicle vehicle) {
        return new VehicleView(vehicle.id(), vehicle.evOwnerId(), vehicle.companyId(),
                vehicle.plateNumber().value(), vehicle.brand(), vehicle.model(),
                vehicle.documentUrl(), vehicle.status().name(), vehicle.createdAt());
    }

    private static EvKycView toEvKycView(KycProfile profile, User user) {
        String resolvedName = profile.fullName() != null
                ? profile.fullName()
                : (user == null ? "" : user.fullName());
        String email = user == null ? "" : user.email();
        return new EvKycView(profile.id(), profile.evOwnerId(), resolvedName, email, profile.phone(),
                profile.country(), profile.address(), profile.birthDate(), profile.documentType(),
                profile.documentNumber(), profile.gender(), profile.kycStatus().name(), profile.createdAt());
    }

    private static UserSummaryView toUserSummaryView(User user) {
        return new UserSummaryView(user.id(), user.email(), user.fullName(), user.role().name(),
                user.status().name(), user.createdAt());
    }
}
