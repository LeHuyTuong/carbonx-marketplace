package com.carbonx.mrv.application.port.in;

import java.math.BigDecimal;
import java.util.List;

public interface MrvQueryUseCase {
    List<EmissionReportView> listMyReports(Long companyId);
    List<EmissionReportView> listPendingReportsForCva();
    List<EmissionReportView> listReportsForAdmin();
    String askCarbonAssistant(Long actorUserId, String actorRole, String message);

    record EmissionReportView(Long id, Long projectId, String projectTitle, Long companyId, String period, BigDecimal totalEnergyKwh, BigDecimal totalCo2Kg, int vehicleCount, String status, BigDecimal aiPreScore, String aiVersion, String aiPreNotes) {}
}
