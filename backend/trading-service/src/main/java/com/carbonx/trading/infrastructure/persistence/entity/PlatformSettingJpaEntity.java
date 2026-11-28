package com.carbonx.trading.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Mutable key-value setting row persisted in {@code platform_settings}. */
@Entity
@Table(name = "platform_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformSettingJpaEntity {

    @Id
    @Column(name = "setting_key", length = 64)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 255)
    private String settingValue;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public static PlatformSettingJpaEntity feeRate(String key, String value, String description, Long adminUserId) {
        PlatformSettingJpaEntity entity = new PlatformSettingJpaEntity();
        entity.settingKey = key;
        entity.settingValue = value;
        entity.description = description;
        entity.updatedByUserId = adminUserId;
        entity.updatedAt = Instant.now();
        return entity;
    }

    public void updateValue(String value, Long adminUserId) {
        this.settingValue = value;
        this.updatedByUserId = adminUserId;
        this.updatedAt = Instant.now();
    }
}
