package com.carbonx.notification.application.port.out;

/** Outbound Port: Realtime push delivery (SSE / WebSocket). */
public interface RealtimePushPort {
    void pushToUser(Long recipientUserId, String eventType, Object payload);
}
