package com.carbonx.mrv.infrastructure.persistence.adapter;

import com.carbonx.mrv.application.port.out.ProjectRepositoryPort;
import com.carbonx.mrv.domain.project.Project;
import com.carbonx.mrv.domain.project.ProjectStatus;
import com.carbonx.mrv.infrastructure.persistence.entity.ProjectJpaEntity;
import com.carbonx.mrv.infrastructure.persistence.repository.ProjectJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Adapts the Project aggregate to the {@code projects} table. */
@Component
@RequiredArgsConstructor
public class ProjectPersistenceAdapter implements ProjectRepositoryPort {

    private final ProjectJpaRepository repository;

    @Override
    public Optional<Project> findById(Long id) {
        return repository.findById(id).map(ProjectPersistenceAdapter::toDomain);
    }

    @Override
    public List<Project> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return repository.findAllByIdIn(ids).stream().map(ProjectPersistenceAdapter::toDomain).toList();
    }

    @Override
    public List<Project> findAllOrdered() {
        return repository.findAllByOrderByIdDesc().stream().map(ProjectPersistenceAdapter::toDomain).toList();
    }

    @Override
    public Project save(Project project) {
        return toDomain(repository.save(toEntity(project)));
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    private static Project toDomain(ProjectJpaEntity entity) {
        return new Project(entity.getId(), entity.getTitle(), entity.getDescription(),
                entity.getEmissionFactor(), entity.getBufferRate(), entity.getUncertaintyRate(),
                entity.getLeakageRate(), ProjectStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static ProjectJpaEntity toEntity(Project project) {
        return ProjectJpaEntity.builder()
                .id(project.id())
                .title(project.title())
                .description(project.description())
                .emissionFactor(project.emissionFactor())
                .bufferRate(project.bufferRate())
                .uncertaintyRate(project.uncertaintyRate())
                .leakageRate(project.leakageRate())
                .status(project.status().name())
                .createdAt(project.createdAt())
                .updatedAt(project.updatedAt())
                .build();
    }
}
