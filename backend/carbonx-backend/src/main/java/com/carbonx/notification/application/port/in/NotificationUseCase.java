package com.carbonx.notification.application.port.in;

import java.time.Instant;
import java.util.List;

/** Inbound Use-Case Port: User Notifications & Realtime SSE Subscription. */
public interface NotificationUseCase {
    List<NotificationView> listMyNotifications(Long userId);

    void markAllRead(Long userId);

    void clearMyNotifications(Long userId);

    record NotificationView(Long id, String eventType, String title, String body, boolean read, Instant createdAt) {}
}
