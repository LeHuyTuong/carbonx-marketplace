package com.carbonx.trading.infrastructure.persistence.repository;

import com.carbonx.trading.infrastructure.persistence.entity.PlatformSettingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingJpaRepository extends JpaRepository<PlatformSettingJpaEntity, String> {
}
