package com.carbonx.identity.application.service;

import com.carbonx.common.domain.DomainEvent;
import com.carbonx.common.events.IntegrationAggregateType;
import com.carbonx.common.events.IntegrationEventType;
import com.carbonx.common.events.IntegrationEvents.VehicleRegisteredEvent;
import com.carbonx.common.outbox.OutboxPort;
import com.carbonx.identity.application.exception.IdentityApplicationException;
import com.carbonx.identity.application.exception.IdentityErrorCode;
import com.carbonx.identity.application.port.in.IdentityCommandUseCase.AuthResult;
import com.carbonx.identity.application.port.in.IdentityCommandUseCase.LoginCommand;
import com.carbonx.identity.application.port.in.IdentityCommandUseCase.RegisterCommand;
import com.carbonx.identity.application.port.out.CompanyRepositoryPort;
import com.carbonx.identity.application.port.out.KycProfileRepositoryPort;
import com.carbonx.identity.application.port.out.OtpChallengePort;
import com.carbonx.identity.application.port.out.PasswordHasherPort;
import com.carbonx.identity.application.port.out.TokenIssuerPort;
import com.carbonx.identity.application.port.out.TokenIssuerPort.IssuedTokens;
import com.carbonx.identity.application.port.out.UserRepositoryPort;
import com.carbonx.identity.application.port.out.VehicleRepositoryPort;
import com.carbonx.identity.domain.company.Company;
import com.carbonx.identity.domain.company.KycStatus;
import com.carbonx.identity.domain.user.Role;
import com.carbonx.identity.domain.user.User;
import com.carbonx.identity.domain.user.UserStatus;
import com.carbonx.identity.domain.vehicle.PlateNumber;
import com.carbonx.identity.domain.vehicle.Vehicle;
import com.carbonx.identity.domain.vehicle.VehicleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class IdentityCommandServiceTest {

    @Mock
    private UserRepositoryPort userRepository;
    @Mock
    private CompanyRepositoryPort companyRepository;
    @Mock
    private KycProfileRepositoryPort kycProfileRepository;
    @Mock
    private VehicleRepositoryPort vehicleRepository;
    @Mock
    private PasswordHasherPort passwordHasher;
    @Mock
    private TokenIssuerPort tokenIssuer;
    @Mock
    private OtpChallengePort otpChallenge;
    @Mock
    private OutboxPort outbox;

    @InjectMocks
    private IdentityCommandService service;

    @Test
    @DisplayName("register hashes the password, normalises the email and issues a JWT pair")
    void registerIssuesTokensForSelfServiceRole() {
        given(userRepository.existsByEmail("owner@carbonx.vn")).willReturn(false);
        given(passwordHasher.encode("Secret#123")).willReturn("bcrypt-hash");
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> ((User) invocation.getArgument(0)).withId(42L));
        given(tokenIssuer.issue(any(User.class))).willReturn(new IssuedTokens("access", "refresh"));

        AuthResult result = service.register(new RegisterCommand("Owner@CarbonX.vn", "Secret#123",
                "Nguyen Van A", "EV_OWNER"));

        assertThat(result.userId()).isEqualTo(42L);
        assertThat(result.email()).isEqualTo("owner@carbonx.vn");
        assertThat(result.role()).isEqualTo("ROLE_EV_OWNER");
        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
    }

    @Test
    @DisplayName("privileged roles cannot self-register")
    void registerRejectsPrivilegedRole() {
        assertThatThrownBy(() -> service.register(new RegisterCommand("admin@carbonx.vn", "Secret#123",
                "Admin", "ROLE_ADMIN")))
                .isInstanceOfSatisfying(IdentityApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(IdentityErrorCode.ROLE_NOT_SELF_REGISTRABLE));

        then(userRepository).should(never()).save(any(User.class));
    }

    @Test
    @DisplayName("registering an existing email is a conflict")
    void registerRejectsDuplicateEmail() {
        given(userRepository.existsByEmail("owner@carbonx.vn")).willReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterCommand("owner@carbonx.vn", "Secret#123",
                "Nguyen Van A", "EV_OWNER")))
                .isInstanceOfSatisfying(IdentityApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(IdentityErrorCode.EMAIL_ALREADY_USED));

        then(userRepository).should(never()).save(any(User.class));
    }

    @Test
    @DisplayName("login fails with invalid credentials when the password does not match")
    void loginRejectsWrongPassword() {
        given(userRepository.findByEmail("owner@carbonx.vn")).willReturn(Optional.of(activeOwner()));
        given(passwordHasher.verify("WrongPassword", "bcrypt-hash")).willReturn(false);

        assertThatThrownBy(() -> service.login(new LoginCommand("owner@carbonx.vn", "WrongPassword")))
                .isInstanceOfSatisfying(IdentityApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));

        then(tokenIssuer).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("login fails closed for an account that is not activated")
    void loginRejectsInactiveAccount() {
        User pending = User.rehydrate(5L, "owner@carbonx.vn", "bcrypt-hash", "Nguyen Van A",
                Role.ROLE_EV_OWNER, null, UserStatus.PENDING_VERIFICATION, null, null);
        given(userRepository.findByEmail("owner@carbonx.vn")).willReturn(Optional.of(pending));
        given(passwordHasher.verify("Secret#123", "bcrypt-hash")).willReturn(true);

        assertThatThrownBy(() -> service.login(new LoginCommand("owner@carbonx.vn", "Secret#123")))
                .isInstanceOfSatisfying(IdentityApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(IdentityErrorCode.ACCOUNT_NOT_ACTIVE));
    }

    @Test
    @DisplayName("a first Google login provisions an active EV owner without a usable password")
    void googleLoginProvisionsEvOwner() {
        given(userRepository.findByOauthSubject("google-sub-1")).willReturn(Optional.empty());
        given(userRepository.findByEmail("owner@gmail.com")).willReturn(Optional.empty());
        given(passwordHasher.encode(anyString())).willReturn("random-bcrypt");
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> ((User) invocation.getArgument(0)).withId(11L));
        given(tokenIssuer.issue(any(User.class))).willReturn(new IssuedTokens("access", "refresh"));

        AuthResult result = service.loginWithGoogleOAuth2("Owner@Gmail.com", "google-sub-1", "Nguyen Van A");

        assertThat(result.userId()).isEqualTo(11L);
        assertThat(result.role()).isEqualTo("ROLE_EV_OWNER");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getValue().oauthSubject()).isEqualTo("google-sub-1");
        assertThat(saved.getValue().passwordHash()).isEqualTo("random-bcrypt");
    }

    @Test
    @DisplayName("a verified OTP activates a pending account")
    void verifyOtpActivatesPendingAccount() {
        User pending = User.rehydrate(5L, "owner@carbonx.vn", "bcrypt-hash", "Nguyen Van A",
                Role.ROLE_EV_OWNER, null, UserStatus.PENDING_VERIFICATION, null, null);
        given(userRepository.findByEmail("owner@carbonx.vn")).willReturn(Optional.of(pending));
        given(otpChallenge.matches("owner@carbonx.vn", "123456")).willReturn(true);
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.verifyOtp("owner@carbonx.vn", "123456");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(saved.capture());
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    @DisplayName("a wrong OTP never activates the account")
    void verifyOtpRejectsWrongCode() {
        given(userRepository.findByEmail("owner@carbonx.vn")).willReturn(Optional.of(activeOwner()));
        given(otpChallenge.matches("owner@carbonx.vn", "000000")).willReturn(false);

        assertThatThrownBy(() -> service.verifyOtp("owner@carbonx.vn", "000000"))
                .isInstanceOfSatisfying(IdentityApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(IdentityErrorCode.INVALID_OTP));
    }

    @Test
    @DisplayName("registering a vehicle appends VehicleRegisteredEvent to the transactional outbox")
    void registerVehicleAppendsVehicleRegisteredEvent() {
        given(userRepository.findById(7L)).willReturn(Optional.of(activeOwner()));
        given(companyRepository.findById(3L)).willReturn(Optional.of(approvedCompany()));
        given(vehicleRepository.existsByPlateNumber("51A12345")).willReturn(false);
        given(vehicleRepository.save(any(Vehicle.class)))
                .willAnswer(invocation -> ((Vehicle) invocation.getArgument(0)).withId(99L));

        Long vehicleId = service.registerVehicle(7L, 3L, "51a-123.45", "VinFast", "VF8",
                "https://s3.carbonx.vn/docs/vf8.pdf");

        assertThat(vehicleId).isEqualTo(99L);

        ArgumentCaptor<DomainEvent> event = ArgumentCaptor.forClass(DomainEvent.class);
        then(outbox).should().append(event.capture());
        assertThat(event.getValue()).isInstanceOf(VehicleRegisteredEvent.class);
        VehicleRegisteredEvent registered = (VehicleRegisteredEvent) event.getValue();
        assertThat(registered.vehicleId()).isEqualTo(99L);
        assertThat(registered.evOwnerId()).isEqualTo(7L);
        assertThat(registered.companyId()).isEqualTo(3L);
        assertThat(registered.plateNumber()).isEqualTo("51A12345");
        assertThat(registered.eventType()).isEqualTo(IntegrationEventType.IDENTITY_VEHICLE_REGISTERED.wireName());
        assertThat(registered.aggregateType()).isEqualTo(IntegrationAggregateType.VEHICLE.wireName());
    }

    @Test
    @DisplayName("a vehicle cannot be registered under a company whose KYC is not approved")
    void registerVehicleRequiresApprovedCompany() {
        given(userRepository.findById(7L)).willReturn(Optional.of(activeOwner()));
        given(companyRepository.findById(3L)).willReturn(Optional.of(Company.rehydrate(3L, 8L,
                "Green Fleet JSC", "0312345678", "HCM", KycStatus.PENDING, null, null)));

        assertThatThrownBy(() -> service.registerVehicle(7L, 3L, "51A12345", "VinFast", "VF8", null))
                .isInstanceOfSatisfying(IdentityApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(IdentityErrorCode.COMPANY_NOT_APPROVED));

        then(vehicleRepository).should(never()).save(any(Vehicle.class));
        then(outbox).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("a duplicated plate number is rejected before the event is published")
    void registerVehicleRejectsDuplicatePlate() {
        given(userRepository.findById(7L)).willReturn(Optional.of(activeOwner()));
        given(companyRepository.findById(3L)).willReturn(Optional.of(approvedCompany()));
        given(vehicleRepository.existsByPlateNumber("51A12345")).willReturn(true);

        assertThatThrownBy(() -> service.registerVehicle(7L, 3L, "51A-123.45", "VinFast", "VF8", null))
                .isInstanceOfSatisfying(IdentityApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(IdentityErrorCode.VEHICLE_ALREADY_REGISTERED));

        then(outbox).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("an owner cannot delete another owner's vehicle")
    void deleteVehicleEnforcesOwnership() {
        given(vehicleRepository.findById(99L)).willReturn(Optional.of(vehicleOwnedBy(8L)));

        assertThatThrownBy(() -> service.deleteVehicle(7L, "ROLE_EV_OWNER", 99L))
                .isInstanceOfSatisfying(IdentityApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(IdentityErrorCode.FORBIDDEN));

        then(vehicleRepository).should(never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("an ADMIN can delete any vehicle")
    void deleteVehicleAllowsAdmin() {
        given(vehicleRepository.findById(99L)).willReturn(Optional.of(vehicleOwnedBy(8L)));

        service.deleteVehicle(1L, "ROLE_ADMIN", 99L);

        then(vehicleRepository).should().deleteById(99L);
    }

    @Test
    @DisplayName("only an ADMIN may review a company KYC dossier")
    void reviewCompanyKycRequiresAdmin() {
        given(userRepository.findById(7L)).willReturn(Optional.of(activeOwner()));

        assertThatThrownBy(() -> service.reviewCompanyKyc(7L, 3L, true, null))
                .isInstanceOfSatisfying(IdentityApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(IdentityErrorCode.FORBIDDEN));

        then(companyRepository).should(never()).save(any(Company.class));
    }

    @Test
    @DisplayName("approving a company dossier persists the APPROVED decision")
    void reviewCompanyKycApproves() {
        User admin = User.rehydrate(1L, "admin@carbonx.vn", "bcrypt-hash", "Admin",
                Role.ROLE_ADMIN, null, UserStatus.ACTIVE, null, null);
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));
        given(companyRepository.findById(3L)).willReturn(Optional.of(approvedCompany()));
        given(companyRepository.save(any(Company.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.reviewCompanyKyc(1L, 3L, true, "Ho so day du");

        ArgumentCaptor<Company> saved = ArgumentCaptor.forClass(Company.class);
        then(companyRepository).should().save(saved.capture());
        assertThat(saved.getValue().kycStatus()).isEqualTo(KycStatus.APPROVED);
    }

    @Test
    @DisplayName("submitting an EV owner KYC dossier for a COMPANY account is forbidden")
    void submitEvOwnerKycRequiresEvOwnerRole() {
        given(userRepository.findById(8L)).willReturn(Optional.of(User.rehydrate(8L, "company@carbonx.vn",
                "bcrypt-hash", "Green Fleet", Role.ROLE_COMPANY, null, UserStatus.ACTIVE, null, null)));

        assertThatThrownBy(() -> service.submitEvOwnerKyc(8L, "CCCD", "079123456789", "0901234567"))
                .isInstanceOfSatisfying(IdentityApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(IdentityErrorCode.FORBIDDEN));

        then(kycProfileRepository).shouldHaveNoInteractions();
    }

    private static User activeOwner() {
        return User.rehydrate(7L, "owner@carbonx.vn", "bcrypt-hash", "Nguyen Van A",
                Role.ROLE_EV_OWNER, null, UserStatus.ACTIVE, null, null);
    }

    private static Company approvedCompany() {
        return Company.rehydrate(3L, 8L, "Green Fleet JSC", "0312345678", "Ho Chi Minh City",
                KycStatus.APPROVED, null, null);
    }

    private static Vehicle vehicleOwnedBy(Long evOwnerId) {
        return Vehicle.rehydrate(99L, evOwnerId, 3L, new PlateNumber("51A12345"), "VinFast", "VF8",
                null, VehicleStatus.ACTIVE, null, null);
    }
}
