package com.carbonx.mrv.infrastructure.persistence.repository;

import com.carbonx.mrv.infrastructure.persistence.entity.ProjectJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProjectJpaRepository extends JpaRepository<ProjectJpaEntity, Long> {
    List<ProjectJpaEntity> findAllByIdIn(Collection<Long> ids);

    List<ProjectJpaEntity> findAllByOrderByIdDesc();
}
