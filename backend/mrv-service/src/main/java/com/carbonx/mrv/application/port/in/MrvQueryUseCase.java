package com.carbonx.mrv.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface MrvQueryUseCase {
    List<ProjectView> listProjects();
    List<ProjectApplicationView> listMyApplications(Long companyId);
    List<ProjectApplicationView> listPendingApplicationsForCva();
    List<ProjectApplicationView> listAllApplications();
    List<EmissionReportView> listMyReports(Long companyId);
    List<EmissionReportView> listPendingReportsForCva();
    List<EmissionReportView> listReportsForAdmin();
    EmissionReportView getReportById(Long reportId, Long callerCompanyId, boolean isReviewerOrAdmin);
    String askCarbonAssistant(Long actorUserId, String actorRole, String message);

    record ProjectView(Long id, String title, String description, String status,
                       BigDecimal emissionFactor, BigDecimal bufferRate, BigDecimal uncertaintyRate,
                       BigDecimal leakageRate, Instant createdAt) {}

    record ProjectApplicationView(Long id, Long projectId, String projectTitle, Long companyId,
                                  String companyNameSnap, String documentUrl, String status,
                                  Long cvaReviewerId, String cvaNote, Long adminReviewerId, String adminNote,
                                  Instant submittedAt, Instant updatedAt) {}

    record EmissionReportView(Long id, Long projectId, String projectTitle, Long companyId, String period, BigDecimal totalEnergyKwh, BigDecimal totalCo2Kg, int vehicleCount, String status, BigDecimal aiPreScore, String aiVersion, String aiPreNotes) {}
}
