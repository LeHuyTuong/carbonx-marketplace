CREATE TABLE IF NOT EXISTS platform_settings (
    setting_key        VARCHAR(64)  PRIMARY KEY,
    setting_value      VARCHAR(255) NOT NULL,
    description        VARCHAR(255),
    updated_by_user_id BIGINT,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO platform_settings (setting_key, setting_value, description)
VALUES ('trading.fee_rate', '0.0500', 'Platform transaction commission rate (0.0500 = 5%)')
ON DUPLICATE KEY UPDATE setting_key = setting_key;
