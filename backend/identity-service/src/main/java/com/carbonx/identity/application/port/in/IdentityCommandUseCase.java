package com.carbonx.identity.application.port.in;

import java.time.Instant;

public interface IdentityCommandUseCase {
    AuthResult register(RegisterCommand command);
    AuthResult login(LoginCommand command);
    AuthResult loginWithGoogleOAuth2(String email, String googleSub, String fullName);
    void verifyOtp(String email, String otpCode);
    void changePassword(Long userId, String oldPassword, String newPassword, String confirmPassword);
    UserAdminResult provisionUserByAdmin(Long adminUserId, AdminProvisionUserCommand command);
    UserAdminResult updateUserStatus(Long adminUserId, Long targetUserId, String status);
    void submitEvOwnerKyc(Long evOwnerId, String documentType, String documentNumber, String phone);
    void submitEvOwnerKyc(Long evOwnerId, EvKycCommand command);
    void submitCompanyKyc(Long userId, String businessName, String taxCode, String address);
    void submitCompanyKyc(Long userId, String businessName, String taxCode, String businessLicense, String address);
    void reviewCompanyKyc(Long adminUserId, Long companyId, boolean approved, String note);
    Long registerVehicle(Long evOwnerId, Long companyId, String plateNumber, String brand, String model, String documentUrl);
    Long updateVehicle(Long evOwnerId, Long vehicleId, Long companyId, String plateNumber, String brand, String model, String documentUrl);
    void deleteVehicle(Long actorUserId, String actorRole, Long vehicleId);

    record RegisterCommand(String email, String password, String fullName, String role) {}
    record LoginCommand(String email, String password) {}
    record AdminProvisionUserCommand(String email, String password, String fullName, String role) {}
    record EvKycCommand(String name, String phone, String country, String address, String birthDate,
                        String documentType, String documentNumber, String gender) {}
    record UserAdminResult(Long id, String email, String fullName, String role, String status, Instant createdAt) {}
    record AuthResult(String accessToken, String refreshToken, Long userId, Long companyId, String email, String role) {
        public AuthResult(String accessToken, String refreshToken, Long userId, String email, String role) {
            this(accessToken, refreshToken, userId, null, email, role);
        }
    }
}
