package com.carbonx.mrv.application.port.out;

import com.carbonx.mrv.domain.report.EmissionReport;
import com.carbonx.mrv.domain.report.ReportStatus;

import java.util.List;
import java.util.Optional;

/** Outbound persistence port for emission reports. */
public interface EmissionReportRepositoryPort {
    Optional<EmissionReport> findById(Long id);

    List<EmissionReport> findByCompanyId(Long companyId);

    List<EmissionReport> findByStatus(ReportStatus status);

    List<EmissionReport> findAll();

    EmissionReport save(EmissionReport report);
}
