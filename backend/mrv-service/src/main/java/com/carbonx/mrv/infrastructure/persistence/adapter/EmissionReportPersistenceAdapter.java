package com.carbonx.mrv.infrastructure.persistence.adapter;

import com.carbonx.mrv.application.port.out.EmissionReportRepositoryPort;
import com.carbonx.mrv.domain.report.EmissionReport;
import com.carbonx.mrv.domain.report.ReportStatus;
import com.carbonx.mrv.infrastructure.persistence.entity.EmissionReportJpaEntity;
import com.carbonx.mrv.infrastructure.persistence.repository.EmissionReportJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adapts the EmissionReport aggregate to the {@code emission_reports} table. */
@Component
@RequiredArgsConstructor
public class EmissionReportPersistenceAdapter implements EmissionReportRepositoryPort {

    private final EmissionReportJpaRepository repository;

    @Override
    public Optional<EmissionReport> findById(Long id) {
        return repository.findById(id).map(EmissionReportPersistenceAdapter::toDomain);
    }

    @Override
    public List<EmissionReport> findByCompanyId(Long companyId) {
        return repository.findByCompanyIdOrderBySubmittedAtDesc(companyId).stream()
                .map(EmissionReportPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<EmissionReport> findByStatus(ReportStatus status) {
        return repository.findByStatusOrderBySubmittedAtAsc(status.name()).stream()
                .map(EmissionReportPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<EmissionReport> findAll() {
        return repository.findAllByOrderBySubmittedAtDesc().stream()
                .map(EmissionReportPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public EmissionReport save(EmissionReport report) {
        return toDomain(repository.save(toEntity(report)));
    }

    private static EmissionReport toDomain(EmissionReportJpaEntity entity) {
        return new EmissionReport(entity.getId(), entity.getApplicationId(), entity.getProjectId(),
                entity.getCompanyId(), entity.getPeriod(), entity.getTotalEnergyKwh(), entity.getTotalCo2Kg(),
                entity.getVehicleCount(), entity.getFileSha256(), entity.getOriginalFilename(),
                ReportStatus.valueOf(entity.getStatus()), entity.getVerifierId(), entity.getAiPreScore(),
                entity.getAiVersion(), entity.getAiPreNotes(), entity.getSubmittedAt(), entity.getUpdatedAt());
    }

    private static EmissionReportJpaEntity toEntity(EmissionReport report) {
        return EmissionReportJpaEntity.builder()
                .id(report.id())
                .applicationId(report.applicationId())
                .projectId(report.projectId())
                .companyId(report.companyId())
                .period(report.period())
                .totalEnergyKwh(report.totalEnergyKwh())
                .totalCo2Kg(report.totalCo2Kg())
                .vehicleCount(report.vehicleCount())
                .fileSha256(report.fileSha256())
                .originalFilename(report.originalFilename())
                .status(report.status().name())
                .verifierId(report.verifierId())
                .aiPreScore(report.aiPreScore())
                .aiVersion(report.aiVersion())
                .aiPreNotes(report.aiPreNotes())
                .submittedAt(report.submittedAt())
                .updatedAt(report.updatedAt())
                .build();
    }
}
