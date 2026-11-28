package com.carbonx.mrv.infrastructure.persistence.repository;

import com.carbonx.mrv.infrastructure.persistence.entity.OutboxEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, Long> {
    List<OutboxEventJpaEntity> findTop50ByStatusOrderByCreatedAtAsc(String status);
}
