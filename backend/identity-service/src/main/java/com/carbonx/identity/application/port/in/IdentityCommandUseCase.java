package com.carbonx.identity.application.port.in;

public interface IdentityCommandUseCase {
    AuthResult register(RegisterCommand command);
    AuthResult login(LoginCommand command);
    AuthResult loginWithGoogleOAuth2(String email, String googleSub, String fullName);
    void verifyOtp(String email, String otpCode);
    void submitEvOwnerKyc(Long evOwnerId, String documentType, String documentNumber, String phone);
    void submitCompanyKyc(Long userId, String businessName, String taxCode, String address);
    void reviewCompanyKyc(Long adminUserId, Long companyId, boolean approved, String note);
    Long registerVehicle(Long evOwnerId, Long companyId, String plateNumber, String brand, String model, String documentUrl);
    void deleteVehicle(Long actorUserId, String actorRole, Long vehicleId);

    record RegisterCommand(String email, String password, String fullName, String role) {}
    record LoginCommand(String email, String password) {}
    record AuthResult(String accessToken, String refreshToken, Long userId, String email, String role) {}
}
