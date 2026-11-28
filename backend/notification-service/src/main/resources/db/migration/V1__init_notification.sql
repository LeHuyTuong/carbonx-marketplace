CREATE TABLE IF NOT EXISTS notifications (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id          VARCHAR(64)  NOT NULL UNIQUE,
    recipient_user_id BIGINT       NOT NULL,
    event_type        VARCHAR(64)  NOT NULL,
    title             VARCHAR(255) NOT NULL,
    body              TEXT,
    is_read           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notif_user (recipient_user_id, is_read)
);
