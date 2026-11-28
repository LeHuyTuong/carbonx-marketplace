package com.carbonx.mrv.application.port.out;

import com.carbonx.mrv.domain.project.Project;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Outbound persistence port for the Project aggregate. */
public interface ProjectRepositoryPort {
    Optional<Project> findById(Long id);

    List<Project> findAllByIds(Collection<Long> ids);

    List<Project> findAllOrdered();

    Project save(Project project);

    void deleteById(Long id);
}
