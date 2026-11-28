package com.carbonx.mrv.application.port.in;

import java.math.BigDecimal;
import java.util.List;

/** Inbound Use-Case Port: Carbon Projects & 2-level Application Approval (CVA -> Admin). */
public interface ProjectUseCase {
    ProjectView createProject(Long adminId, CreateProjectCommand command);

    ProjectView updateProject(Long adminId, Long projectId, UpdateProjectCommand command);

    List<ProjectView> listPublicProjects();

    ApplicationView submitApplication(Long companyId, Long projectId, String documentUrl);

    ApplicationView reviewByCva(Long cvaUserId, Long applicationId, ReviewDecision decision);

    ApplicationView reviewByAdmin(Long adminUserId, Long applicationId, ReviewDecision decision);

    List<ApplicationView> listMyApplications(Long companyId);

    List<ApplicationView> listApplicationsByStatus(String status);

    record CreateProjectCommand(
            String title,
            String description,
            BigDecimal emissionFactor,
            BigDecimal bufferRate,
            BigDecimal uncertaintyRate,
            BigDecimal leakageRate
    ) {}

    record UpdateProjectCommand(
            String title,
            String description,
            BigDecimal emissionFactor,
            BigDecimal bufferRate,
            BigDecimal uncertaintyRate,
            BigDecimal leakageRate,
            String status
    ) {}

    record ReviewDecision(String outcome, String note) {} // APPROVED, REJECTED, NEEDS_REVISION

    record ProjectView(Long id, String title, String description, BigDecimal emissionFactor, BigDecimal bufferRate, BigDecimal uncertaintyRate, BigDecimal leakageRate, String status) {}

    record ApplicationView(Long id, Long projectId, String projectTitle, Long companyId, String companyName, String documentUrl, String status, String cvaNote, String adminNote) {}
}
