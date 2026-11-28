package com.carbonx.identity.infrastructure.persistence.repository;

import com.carbonx.identity.infrastructure.persistence.entity.OutboxEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, Long> {
    List<OutboxEventJpaEntity> findTop50ByStatusOrderByCreatedAtAsc(String status);
}
