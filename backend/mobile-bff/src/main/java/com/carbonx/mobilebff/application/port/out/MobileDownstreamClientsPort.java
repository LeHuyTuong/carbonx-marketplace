package com.carbonx.mobilebff.application.port.out;

import com.carbonx.common.domain.Money;
import com.carbonx.mobilebff.application.port.in.MobileBffCommandUseCase;
import com.carbonx.mobilebff.application.port.in.MobileBffQueryUseCase;
import java.util.List;

/**
 * Outbound Downstream Ports for Mobile BFF.
 * Implemented via @FeignClient + Resilience4j (@CircuitBreaker, @TimeLimiter, @Bulkhead) and AWS S3 Presigner.
 */
public interface MobileDownstreamClientsPort {

    EvProfileSummary fetchEvProfileSummary(Long userId);

    List<MobileBffQueryUseCase.MobileVehicleCardView> fetchEvVehicles(Long userId);

    Money fetchEvWalletBalance(Long userId);

    MobileBffQueryUseCase.MobilePayoutFeedSlice fetchEvPayoutSlice(Long userId, String cursor, int limit);

    int fetchUnreadNotificationCount(Long userId);

    MobileBffCommandUseCase.PresignedCameraUploadGrant generateS3PresignedPutUrl(
            Long userId,
            MobileBffCommandUseCase.UploadDocumentType documentType,
            String contentType,
            long contentLengthBytes
    );

    Long forwardVehicleRegistration(
            String idempotencyKey,
            Long userId,
            String licensePlate,
            String vin,
            String vehicleModel,
            String s3ObjectKey
    );

    void upsertDevicePushToken(Long userId, MobileBffCommandUseCase.PushPlatform platform, String deviceToken, String appVersion);

    void deleteDevicePushToken(Long userId, String deviceToken);

    Long forwardWithdrawalRequest(String idempotencyKey, Long userId, Money amount, String bankCode, String bankAccountNumber);

    record EvProfileSummary(Long userId, String displayName, String kycBadgeStatus, int verifiedVehicleCount) {}
}
