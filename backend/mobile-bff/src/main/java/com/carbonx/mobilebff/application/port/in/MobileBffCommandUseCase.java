package com.carbonx.mobilebff.application.port.in;

import com.carbonx.common.domain.Money;
import java.time.Instant;

/**
 * Inbound CQRS Command Port for CarbonX Mobile App (camera KYC/vehicle presigned upload, push token, 1-tap withdrawal).
 */
public interface MobileBffCommandUseCase {

    PresignedCameraUploadGrant requestCameraUploadUrl(
            Long evOwnerUserId,
            UploadDocumentType documentType,
            String contentType,
            long contentLengthBytes
    );

    Long submitMobileVehicleRegistration(
            String idempotencyKey,
            Long evOwnerUserId,
            String licensePlate,
            String vin,
            String vehicleModel,
            String s3ObjectKey
    );

    void registerMobilePushDevice(Long userId, PushPlatform platform, String deviceToken, String appVersion);

    void revokeMobilePushDevice(Long userId, String deviceToken);

    Long requestMobileWithdrawal(
            String idempotencyKey,
            Long evOwnerUserId,
            Money amount,
            String bankCode,
            String bankAccountNumber
    );

    enum UploadDocumentType {
        KYC_ID_CARD_FRONT,
        KYC_ID_CARD_BACK,
        VEHICLE_REGISTRATION_CERTIFICATE
    }

    enum PushPlatform {
        FCM_ANDROID,
        APNS_IOS
    }

    record PresignedCameraUploadGrant(
            String uploadPutUrl,
            String s3ObjectKey,
            Instant expiresAt,
            long maxAllowedBytes
    ) {}
}
