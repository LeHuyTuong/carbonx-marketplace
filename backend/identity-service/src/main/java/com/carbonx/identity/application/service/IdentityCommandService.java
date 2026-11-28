package com.carbonx.identity.application.service;

import com.carbonx.common.events.IntegrationEvents.VehicleRegisteredEvent;
import com.carbonx.common.outbox.OutboxPort;
import com.carbonx.identity.application.exception.IdentityApplicationException;
import com.carbonx.identity.application.port.in.IdentityCommandUseCase;
import com.carbonx.identity.application.port.out.CompanyRepositoryPort;
import com.carbonx.identity.application.port.out.KycProfileRepositoryPort;
import com.carbonx.identity.application.port.out.OtpChallengePort;
import com.carbonx.identity.application.port.out.PasswordHasherPort;
import com.carbonx.identity.application.port.out.TokenIssuerPort;
import com.carbonx.identity.application.port.out.UserRepositoryPort;
import com.carbonx.identity.application.port.out.VehicleRepositoryPort;
import com.carbonx.identity.domain.company.Company;
import com.carbonx.identity.domain.kyc.KycProfile;
import com.carbonx.identity.domain.user.Role;
import com.carbonx.identity.domain.user.User;
import com.carbonx.identity.domain.user.UserStatus;
import com.carbonx.identity.domain.vehicle.PlateNumber;
import com.carbonx.identity.domain.vehicle.Vehicle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

import static com.carbonx.identity.application.exception.IdentityErrorCode.ACCOUNT_NOT_ACTIVE;
import static com.carbonx.identity.application.exception.IdentityErrorCode.COMPANY_NOT_APPROVED;
import static com.carbonx.identity.application.exception.IdentityErrorCode.EMAIL_ALREADY_USED;
import static com.carbonx.identity.application.exception.IdentityErrorCode.FORBIDDEN;
import static com.carbonx.identity.application.exception.IdentityErrorCode.INVALID_CREDENTIALS;
import static com.carbonx.identity.application.exception.IdentityErrorCode.INVALID_OTP;
import static com.carbonx.identity.application.exception.IdentityErrorCode.ROLE_NOT_SELF_REGISTRABLE;
import static com.carbonx.identity.application.exception.IdentityErrorCode.VEHICLE_ALREADY_REGISTERED;

/**
 * Command side of the identity CQRS split. Every method runs in one local database transaction;
 * the VehicleRegisteredEvent is appended to the transactional outbox in that same transaction.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class IdentityCommandService implements IdentityCommandUseCase {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final UserRepositoryPort userRepository;
    private final CompanyRepositoryPort companyRepository;
    private final KycProfileRepositoryPort kycProfileRepository;
    private final VehicleRepositoryPort vehicleRepository;
    private final PasswordHasherPort passwordHasher;
    private final TokenIssuerPort tokenIssuer;
    private final OtpChallengePort otpChallenge;
    private final OutboxPort outbox;

    @Override
    public AuthResult register(RegisterCommand command) {
        if (command == null) {
            throw IdentityApplicationException.badRequest("Registration payload is required");
        }
        Role role = Role.from(command.role());
        if (!role.selfRegistrable()) {
            throw new IdentityApplicationException(ROLE_NOT_SELF_REGISTRABLE,
                    "Role " + role + " cannot be self-registered");
        }
        String email = normalizeEmail(command.email());
        if (userRepository.existsByEmail(email)) {
            throw new IdentityApplicationException(EMAIL_ALREADY_USED, "Email is already registered: " + email);
        }
        String rawPassword = requireUsablePassword(command.password());
        User saved = userRepository.save(User.register(email, passwordHasher.encode(rawPassword),
                command.fullName(), role));
        return authResult(saved);
    }

    @Override
    public AuthResult login(LoginCommand command) {
        if (command == null) {
            throw IdentityApplicationException.badRequest("Login payload is required");
        }
        User user = userRepository.findByEmail(normalizeEmail(command.email()))
                .orElseThrow(() -> new IdentityApplicationException(INVALID_CREDENTIALS, "Invalid email or password"));
        if (!passwordHasher.verify(command.password(), user.passwordHash())) {
            throw new IdentityApplicationException(INVALID_CREDENTIALS, "Invalid email or password");
        }
        return authResult(requireActive(user));
    }

    @Override
    public AuthResult loginWithGoogleOAuth2(String email, String googleSub, String fullName) {
        String normalizedEmail = normalizeEmail(email);
        if (googleSub == null || googleSub.isBlank()) {
            throw IdentityApplicationException.badRequest("Google subject is required");
        }
        User user = userRepository.findByOauthSubject(googleSub)
                .or(() -> userRepository.findByEmail(normalizedEmail))
                .orElse(null);

        if (user == null) {
            User created = User.externalIdentity(normalizedEmail,
                    passwordHasher.encode(UUID.randomUUID().toString()), fullName, googleSub);
            return authResult(userRepository.save(created));
        }
        if (user.oauthSubject() == null) {
            user = userRepository.save(user.linkOauthSubject(googleSub));
        }
        return authResult(requireActive(user));
    }

    @Override
    public void verifyOtp(String email, String otpCode) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> IdentityApplicationException.notFound("User", email));
        if (!otpChallenge.matches(user.email(), otpCode)) {
            throw new IdentityApplicationException(INVALID_OTP, "Invalid or expired OTP code");
        }
        if (!user.isActive()) {
            userRepository.save(user.activate());
        }
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword, String confirmPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> IdentityApplicationException.notFound("User", userId));
        if (oldPassword == null || !passwordHasher.verify(oldPassword, user.passwordHash())) {
            throw new IdentityApplicationException(INVALID_CREDENTIALS, "Current password is incorrect");
        }
        String usable = requireUsablePassword(newPassword);
        if (!usable.equals(confirmPassword)) {
            throw IdentityApplicationException.badRequest("Password confirmation does not match");
        }
        userRepository.save(user.withPasswordHash(passwordHasher.encode(usable)));
    }

    @Override
    public UserAdminResult provisionUserByAdmin(Long adminUserId, AdminProvisionUserCommand command) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> IdentityApplicationException.notFound("Admin", adminUserId));
        if (!admin.is(Role.ROLE_ADMIN)) {
            throw new IdentityApplicationException(FORBIDDEN, "Only ADMIN can provision platform accounts");
        }
        if (command == null) {
            throw IdentityApplicationException.badRequest("Provisioning payload is required");
        }
        Role role = Role.from(command.role());
        String email = normalizeEmail(command.email());
        if (userRepository.existsByEmail(email)) {
            throw new IdentityApplicationException(EMAIL_ALREADY_USED, "Email is already registered: " + email);
        }
        String rawPassword = requireUsablePassword(command.password());
        User saved = userRepository.save(User.register(email, passwordHasher.encode(rawPassword),
                command.fullName(), role));
        log.info("Admin {} provisioned user {} with role {}", adminUserId, saved.id(), role);
        return toUserAdminResult(saved);
    }

    @Override
    public UserAdminResult updateUserStatus(Long adminUserId, Long targetUserId, String status) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> IdentityApplicationException.notFound("Admin", adminUserId));
        if (!admin.is(Role.ROLE_ADMIN)) {
            throw new IdentityApplicationException(FORBIDDEN, "Only ADMIN can update user status");
        }
        if (adminUserId.equals(targetUserId)) {
            throw IdentityApplicationException.badRequest("Administrators cannot modify their own status");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> IdentityApplicationException.notFound("User", targetUserId));
        UserStatus nextStatus = parseUserStatus(status);
        User updated = userRepository.save(target.withStatus(nextStatus));
        return toUserAdminResult(updated);
    }

    @Override
    public void submitEvOwnerKyc(Long evOwnerId, String documentType, String documentNumber, String phone) {
        submitEvOwnerKyc(evOwnerId, new EvKycCommand(null, phone, null, null, null,
                documentType, documentNumber, null));
    }

    @Override
    public void submitEvOwnerKyc(Long evOwnerId, EvKycCommand command) {
        if (command == null) {
            throw IdentityApplicationException.badRequest("KYC payload is required");
        }
        User owner = userRepository.findById(evOwnerId)
                .orElseThrow(() -> IdentityApplicationException.notFound("User", evOwnerId));
        if (!owner.is(Role.ROLE_EV_OWNER)) {
            throw new IdentityApplicationException(FORBIDDEN, "Only EV owners can submit an EV owner KYC dossier");
        }
        KycProfile profile = kycProfileRepository.findByEvOwnerId(evOwnerId)
                .map(existing -> existing.updateDocument(command.name(), command.documentType(),
                        command.documentNumber(), command.phone(), command.country(),
                        command.address(), command.birthDate(), command.gender()))
                .orElseGet(() -> KycProfile.submit(evOwnerId, command.name(), command.documentType(),
                        command.documentNumber(), command.phone(), command.country(),
                        command.address(), command.birthDate(), command.gender()));
        kycProfileRepository.save(profile);
    }

    @Override
    public void submitCompanyKyc(Long userId, String businessName, String taxCode, String address) {
        submitCompanyKyc(userId, businessName, taxCode, null, address);
    }

    @Override
    public void submitCompanyKyc(Long userId, String businessName, String taxCode, String businessLicense,
                                 String address) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> IdentityApplicationException.notFound("User", userId));
        if (!user.is(Role.ROLE_COMPANY)) {
            throw new IdentityApplicationException(FORBIDDEN, "Only COMPANY accounts can submit a company KYC dossier");
        }
        Company company = companyRepository.findByUserId(userId)
                .map(existing -> existing.updateProfile(businessName, taxCode, businessLicense, address))
                .orElseGet(() -> Company.submit(userId, businessName, taxCode, businessLicense, address));
        companyRepository.save(company);
    }

    @Override
    public void reviewCompanyKyc(Long adminUserId, Long companyId, boolean approved, String note) {
        User reviewer = userRepository.findById(adminUserId)
                .orElseThrow(() -> IdentityApplicationException.notFound("User", adminUserId));
        if (!reviewer.is(Role.ROLE_ADMIN)) {
            throw new IdentityApplicationException(FORBIDDEN, "Only ADMIN can review company KYC dossiers");
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> IdentityApplicationException.notFound("Company", companyId));
        Company decided = approved ? company.approve() : company.reject();
        companyRepository.save(decided);
        // The dossier note is part of the reviewer trail; the companies table has no note column,
        // so only the decision is persisted here (notification-service carries the human message).
        log.info("Company KYC reviewed companyId={} approved={} reviewerId={} notePresent={}",
                companyId, approved, adminUserId, note != null && !note.isBlank());
    }

    @Override
    public Long registerVehicle(Long evOwnerId, Long companyId, String plateNumber, String brand,
                                String model, String documentUrl) {
        User owner = userRepository.findById(evOwnerId)
                .orElseThrow(() -> IdentityApplicationException.notFound("User", evOwnerId));
        if (!owner.is(Role.ROLE_EV_OWNER)) {
            throw new IdentityApplicationException(FORBIDDEN, "Only EV owners can register vehicles");
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> IdentityApplicationException.notFound("Company", companyId));
        if (!company.isApproved()) {
            throw new IdentityApplicationException(COMPANY_NOT_APPROVED,
                    "Company KYC is not approved yet: " + companyId);
        }
        PlateNumber plate = new PlateNumber(plateNumber);
        if (vehicleRepository.existsByPlateNumber(plate.value())) {
            throw new IdentityApplicationException(VEHICLE_ALREADY_REGISTERED,
                    "Plate number is already registered: " + plate.value());
        }
        Vehicle saved = vehicleRepository.save(
                Vehicle.register(evOwnerId, companyId, plate, brand, model, documentUrl));

        outbox.append(new VehicleRegisteredEvent(saved.id(), evOwnerId, companyId, plate.value(),
                saved.brand(), saved.model()));
        return saved.id();
    }

    @Override
    public Long updateVehicle(Long evOwnerId, Long vehicleId, Long companyId, String plateNumber, String brand,
                              String model, String documentUrl) {
        Vehicle existing = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> IdentityApplicationException.notFound("Vehicle", vehicleId));
        if (!existing.isOwnedBy(evOwnerId)) {
            throw new IdentityApplicationException(FORBIDDEN, "Vehicle does not belong to the current user");
        }
        Long targetCompanyId = companyId == null ? existing.companyId() : companyId;
        Company company = companyRepository.findById(targetCompanyId)
                .orElseThrow(() -> IdentityApplicationException.notFound("Company", targetCompanyId));
        if (!company.isApproved()) {
            throw new IdentityApplicationException(COMPANY_NOT_APPROVED,
                    "Company KYC is not approved yet: " + targetCompanyId);
        }
        PlateNumber nextPlate = plateNumber == null || plateNumber.isBlank()
                ? existing.plateNumber()
                : new PlateNumber(plateNumber);
        if (!existing.plateNumber().value().equals(nextPlate.value())
                && vehicleRepository.existsByPlateNumber(nextPlate.value())) {
            throw new IdentityApplicationException(VEHICLE_ALREADY_REGISTERED,
                    "Plate number is already registered: " + nextPlate.value());
        }
        Vehicle saved = vehicleRepository.save(existing.updateDetails(
                targetCompanyId, nextPlate, brand, model, documentUrl));
        return saved.id();
    }

    @Override
    public void deleteVehicle(Long actorUserId, String actorRole, Long vehicleId) {
        Role role = Role.from(actorRole);
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> IdentityApplicationException.notFound("Vehicle", vehicleId));
        if (role != Role.ROLE_ADMIN && !vehicle.isOwnedBy(actorUserId)) {
            throw new IdentityApplicationException(FORBIDDEN, "Vehicle does not belong to the current user");
        }
        vehicleRepository.deleteById(vehicleId);
    }

    private AuthResult authResult(User user) {
        Long companyId = user.is(Role.ROLE_COMPANY) && user.id() != null
                ? companyRepository.findByUserId(user.id()).map(Company::id).orElse(null)
                : null;
        TokenIssuerPort.IssuedTokens tokens = companyId != null
                ? tokenIssuer.issue(user, companyId)
                : tokenIssuer.issue(user);
        return new AuthResult(tokens.accessToken(), tokens.refreshToken(), user.id(), companyId, user.email(),
                user.role().name());
    }

    private static UserAdminResult toUserAdminResult(User user) {
        return new UserAdminResult(user.id(), user.email(), user.fullName(), user.role().name(),
                user.status().name(), user.createdAt());
    }

    private static UserStatus parseUserStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw IdentityApplicationException.badRequest("Status is required");
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> UserStatus.ACTIVE;
            case "PENDING", "PENDING_VERIFICATION" -> UserStatus.PENDING_VERIFICATION;
            case "SUSPENDED", "INACTIVE", "BANNED" -> UserStatus.SUSPENDED;
            default -> throw IdentityApplicationException.badRequest("Unsupported status: " + raw);
        };
    }

    private static User requireActive(User user) {
        if (!user.isActive()) {
            throw new IdentityApplicationException(ACCOUNT_NOT_ACTIVE, "Account is not activated yet");
        }
        return user;
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw IdentityApplicationException.badRequest("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireUsablePassword(String password) {
        if (password == null) {
            throw IdentityApplicationException.badRequest("Password is required");
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw IdentityApplicationException.badRequest(
                    "Password length must be between " + MIN_PASSWORD_LENGTH + " and "
                            + MAX_PASSWORD_LENGTH + " characters");
        }
        return password;
    }
}
