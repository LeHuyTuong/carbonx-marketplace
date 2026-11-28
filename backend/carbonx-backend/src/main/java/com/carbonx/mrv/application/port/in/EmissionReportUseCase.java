package com.carbonx.mrv.application.port.in;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

/** Inbound Use-Case Port: CSV Emission Reports, 8 Fraud Rules, AI Scoring & 2-level Verification. */
public interface EmissionReportUseCase {
    EmissionReportView uploadCsvReport(Long companyId, Long projectId, String originalFilename, InputStream csvStream);

    List<FraudRuleResultView> runFraudAnalysis(Long reportId);

    BigDecimal scoreReportWithAi(Long cvaUserId, Long reportId);

    EmissionReportView verifyByCva(Long cvaUserId, Long reportId, boolean approved, String note);

    EmissionReportView approveByAdmin(Long adminUserId, Long reportId, boolean approved, String note);

    List<EmissionReportView> listMyReports(Long companyId);

    List<EmissionReportView> listPendingReportsForCva();

    List<EmissionReportView> listReportsForAdmin();

    record EmissionReportView(
            Long id,
            Long projectId,
            String projectTitle,
            Long companyId,
            String period,
            BigDecimal totalEnergyKwh,
            BigDecimal totalCo2Kg,
            int vehicleCount,
            String status,
            BigDecimal aiRiskScore
    ) {}

    record FraudRuleResultView(String ruleCode, String ruleName, int score, int maxScore, boolean passed, String evidence) {}
}
