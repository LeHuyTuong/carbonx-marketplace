package com.carbonx.mrv.infrastructure.persistence.repository;

import com.carbonx.mrv.infrastructure.persistence.entity.ProjectApplicationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectApplicationJpaRepository extends JpaRepository<ProjectApplicationJpaEntity, Long> {

    Optional<ProjectApplicationJpaEntity> findFirstByProjectIdAndCompanyIdAndStatusOrderByIdDesc(
            Long projectId, Long companyId, String status);

    boolean existsByProjectIdAndCompanyIdAndStatusIn(Long projectId, Long companyId, Collection<String> statuses);

    List<ProjectApplicationJpaEntity> findByCompanyIdOrderBySubmittedAtDesc(Long companyId);

    List<ProjectApplicationJpaEntity> findByStatusOrderBySubmittedAtAsc(String status);

    List<ProjectApplicationJpaEntity> findAllByOrderBySubmittedAtDesc();
}
