package com.carbonx.mrv.application.port.in;

import java.io.InputStream;
import java.math.BigDecimal;

public interface MrvCommandUseCase {
    Long createProject(Long adminId, String title, String description, BigDecimal emissionFactor, BigDecimal bufferRate, BigDecimal uncertaintyRate, BigDecimal leakageRate);
    Long submitApplication(Long companyId, Long projectId, String documentUrl);
    void reviewApplicationByCva(Long cvaUserId, Long applicationId, String outcome, String note);
    void reviewApplicationByAdmin(Long adminUserId, Long applicationId, String outcome, String note);
    Long uploadCsvReport(Long companyId, Long projectId, String originalFilename, InputStream csvStream);
    AiEvaluationView suggestAiScoreForCva(Long actorUserId, Long reportId);
    void verifyReportByCva(Long cvaUserId, Long reportId, boolean approved, String note);
    void approveReportByAdmin(Long adminUserId, Long reportId, boolean approved, String note);

    record AiEvaluationView(Double aiPreScore, String aiVersion, String aiPreNotes) {}
}
