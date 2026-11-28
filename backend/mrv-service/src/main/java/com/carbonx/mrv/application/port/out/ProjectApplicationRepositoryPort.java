package com.carbonx.mrv.application.port.out;

import com.carbonx.mrv.domain.project.ApplicationStatus;
import com.carbonx.mrv.domain.project.ProjectApplication;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Outbound persistence port for project applications. */
public interface ProjectApplicationRepositoryPort {
    Optional<ProjectApplication> findById(Long id);

    /** The approval that unlocks emission reporting for a company/project pair. */
    Optional<ProjectApplication> findApprovedByCompanyAndProject(Long companyId, Long projectId);

    boolean existsByStatuses(Long projectId, Long companyId, Collection<ApplicationStatus> statuses);

    List<ProjectApplication> findByCompanyId(Long companyId);

    List<ProjectApplication> findByStatus(ApplicationStatus status);

    List<ProjectApplication> findAllOrdered();

    ProjectApplication save(ProjectApplication application);
}
