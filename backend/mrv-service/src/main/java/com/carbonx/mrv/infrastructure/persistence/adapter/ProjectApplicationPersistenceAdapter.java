package com.carbonx.mrv.infrastructure.persistence.adapter;

import com.carbonx.mrv.application.port.out.ProjectApplicationRepositoryPort;
import com.carbonx.mrv.domain.project.ApplicationStatus;
import com.carbonx.mrv.domain.project.ProjectApplication;
import com.carbonx.mrv.infrastructure.persistence.entity.ProjectApplicationJpaEntity;
import com.carbonx.mrv.infrastructure.persistence.repository.ProjectApplicationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Adapts project applications to the {@code project_applications} table. */
@Component
@RequiredArgsConstructor
public class ProjectApplicationPersistenceAdapter implements ProjectApplicationRepositoryPort {

    private final ProjectApplicationJpaRepository repository;

    @Override
    public Optional<ProjectApplication> findById(Long id) {
        return repository.findById(id).map(ProjectApplicationPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<ProjectApplication> findApprovedByCompanyAndProject(Long companyId, Long projectId) {
        return repository.findFirstByProjectIdAndCompanyIdAndStatusOrderByIdDesc(
                        projectId, companyId, ApplicationStatus.APPROVED.name())
                .map(ProjectApplicationPersistenceAdapter::toDomain);
    }

    @Override
    public boolean existsByStatuses(Long projectId, Long companyId, Collection<ApplicationStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return false;
        }
        return repository.existsByProjectIdAndCompanyIdAndStatusIn(projectId, companyId,
                statuses.stream().map(Enum::name).toList());
    }

    @Override
    public List<ProjectApplication> findByCompanyId(Long companyId) {
        return repository.findByCompanyIdOrderBySubmittedAtDesc(companyId).stream()
                .map(ProjectApplicationPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<ProjectApplication> findByStatus(ApplicationStatus status) {
        return repository.findByStatusOrderBySubmittedAtAsc(status.name()).stream()
                .map(ProjectApplicationPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<ProjectApplication> findAllOrdered() {
        return repository.findAllByOrderBySubmittedAtDesc().stream()
                .map(ProjectApplicationPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public ProjectApplication save(ProjectApplication application) {
        return toDomain(repository.save(toEntity(application)));
    }

    private static ProjectApplication toDomain(ProjectApplicationJpaEntity entity) {
        return new ProjectApplication(entity.getId(), entity.getProjectId(), entity.getCompanyId(),
                entity.getCompanyNameSnap(), entity.getDocumentUrl(), ApplicationStatus.valueOf(entity.getStatus()),
                entity.getCvaReviewerId(), entity.getCvaNote(), entity.getAdminReviewerId(), entity.getAdminNote(),
                entity.getSubmittedAt(), entity.getUpdatedAt());
    }

    private static ProjectApplicationJpaEntity toEntity(ProjectApplication application) {
        return ProjectApplicationJpaEntity.builder()
                .id(application.id())
                .projectId(application.projectId())
                .companyId(application.companyId())
                .companyNameSnap(application.companyNameSnap())
                .documentUrl(application.documentUrl())
                .status(application.status().name())
                .cvaReviewerId(application.cvaReviewerId())
                .cvaNote(application.cvaNote())
                .adminReviewerId(application.adminReviewerId())
                .adminNote(application.adminNote())
                .submittedAt(application.submittedAt())
                .updatedAt(application.updatedAt())
                .build();
    }
}
