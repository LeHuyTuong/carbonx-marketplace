package com.carbonx.notification.application.port.out;

import java.util.Map;

/**
 * Outbound Port for Mobile Push Notifications (Firebase Cloud Messaging FCM & Apple APNs).
 * Triggered asynchronously when Kafka events (ProfitDistributedEvent, WithdrawalApprovedEvent, KycReviewedEvent) arrive.
 */
public interface MobilePushNotificationPort {
    void sendPushToUserDevices(Long userId, String title, String compactBody, Map<String, String> deepLinkPayload);
}
