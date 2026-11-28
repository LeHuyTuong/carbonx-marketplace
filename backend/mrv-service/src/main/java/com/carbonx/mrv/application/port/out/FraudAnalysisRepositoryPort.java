package com.carbonx.mrv.application.port.out;

import com.carbonx.mrv.domain.fraud.FraudFinding;

import java.util.List;

/** Persists the DQ1..DQ8 audit trail of a report (re-evaluation replaces the previous run). */
public interface FraudAnalysisRepositoryPort {
    void replaceForReport(Long reportId, List<FraudFinding> findings);

    List<FraudFinding> findByReportId(Long reportId);
}
