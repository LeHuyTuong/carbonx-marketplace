package com.carbonx.mrv.application.service;

import com.carbonx.mrv.application.exception.MrvApplicationException;
import com.carbonx.mrv.application.port.in.MrvQueryUseCase;
import com.carbonx.mrv.application.port.out.EmissionReportRepositoryPort;
import com.carbonx.mrv.application.port.out.ProjectApplicationRepositoryPort;
import com.carbonx.mrv.application.port.out.ProjectRepositoryPort;
import com.carbonx.mrv.application.port.out.SpringAiScoringPort;
import com.carbonx.mrv.domain.project.ApplicationStatus;
import com.carbonx.mrv.domain.project.Project;
import com.carbonx.mrv.domain.project.ProjectApplication;
import com.carbonx.mrv.domain.report.EmissionReport;
import com.carbonx.mrv.domain.report.ReportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Read-only side of the MRV CQRS split: no aggregate is ever mutated here. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MrvQueryService implements MrvQueryUseCase {

    private static final int MAX_CHAT_MESSAGE_LENGTH = 2_000;

    private final EmissionReportRepositoryPort reportRepository;
    private final ProjectRepositoryPort projectRepository;
    private final ProjectApplicationRepositoryPort applicationRepository;
    private final SpringAiScoringPort aiScoringPort;

    @Override
    public List<ProjectView> listProjects() {
        return projectRepository.findAllOrdered().stream()
                .map(p -> new ProjectView(p.id(), p.title(), p.description(), p.status().name(),
                        p.emissionFactor(), p.bufferRate(), p.uncertaintyRate(), p.leakageRate(), p.createdAt()))
                .toList();
    }

    @Override
    public List<ProjectApplicationView> listMyApplications(Long companyId) {
        if (companyId == null) {
            throw MrvApplicationException.forbidden("Company identity is required");
        }
        return toApplicationViews(applicationRepository.findByCompanyId(companyId));
    }

    @Override
    public List<ProjectApplicationView> listPendingApplicationsForCva() {
        return toApplicationViews(applicationRepository.findByStatus(ApplicationStatus.SUBMITTED));
    }

    @Override
    public List<ProjectApplicationView> listAllApplications() {
        return toApplicationViews(applicationRepository.findAllOrdered());
    }

    @Override
    public List<EmissionReportView> listMyReports(Long companyId) {
        if (companyId == null) {
            throw MrvApplicationException.forbidden("Company identity is required");
        }
        return toViews(reportRepository.findByCompanyId(companyId));
    }

    @Override
    public List<EmissionReportView> listPendingReportsForCva() {
        return toViews(reportRepository.findByStatus(ReportStatus.SUBMITTED));
    }

    @Override
    public List<EmissionReportView> listReportsForAdmin() {
        return toViews(reportRepository.findAll());
    }

    @Override
    public EmissionReportView getReportById(Long reportId, Long callerCompanyId, boolean isReviewerOrAdmin) {
        EmissionReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> MrvApplicationException.notFound("Report not found: " + reportId));
        if (!isReviewerOrAdmin && (callerCompanyId == null || !callerCompanyId.equals(report.companyId()))) {
            throw MrvApplicationException.forbidden("Report does not belong to your company");
        }
        String title = projectRepository.findById(report.projectId()).map(Project::title).orElse(null);
        return toView(report, title);
    }

    @Override
    public String askCarbonAssistant(Long actorUserId, String actorRole, String message) {
        if (actorUserId == null) {
            throw MrvApplicationException.forbidden("Authenticated user is required");
        }
        if (message == null || message.isBlank()) {
            throw MrvApplicationException.badRequest("Chat message is required");
        }
        String question = message.trim();
        if (question.length() > MAX_CHAT_MESSAGE_LENGTH) {
            throw MrvApplicationException.badRequest(
                    "Chat message must be at most " + MAX_CHAT_MESSAGE_LENGTH + " characters");
        }
        String roleContext = "role=" + (actorRole == null || actorRole.isBlank() ? "UNKNOWN" : actorRole)
                + "; userId=" + actorUserId;
        return aiScoringPort.chatWithAssistant(roleContext, question);
    }

    private List<ProjectApplicationView> toApplicationViews(List<ProjectApplication> applications) {
        if (applications.isEmpty()) {
            return List.of();
        }
        List<Long> projectIds = applications.stream().map(ProjectApplication::projectId).distinct().toList();
        Map<Long, String> titles = projectRepository.findAllByIds(projectIds).stream()
                .collect(Collectors.toMap(Project::id, Project::title, (first, second) -> first));
        return applications.stream()
                .map(a -> new ProjectApplicationView(a.id(), a.projectId(), titles.get(a.projectId()),
                        a.companyId(), a.companyNameSnap(), a.documentUrl(), a.status().name(),
                        a.cvaReviewerId(), a.cvaNote(), a.adminReviewerId(), a.adminNote(),
                        a.submittedAt(), a.updatedAt()))
                .toList();
    }

    private List<EmissionReportView> toViews(List<EmissionReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        List<Long> projectIds = reports.stream().map(EmissionReport::projectId).distinct().toList();
        Map<Long, String> titles = projectRepository.findAllByIds(projectIds).stream()
                .collect(Collectors.toMap(Project::id, Project::title, (first, second) -> first));
        return reports.stream()
                .map(report -> toView(report, titles.get(report.projectId())))
                .toList();
    }

    private static EmissionReportView toView(EmissionReport report, String projectTitle) {
        return new EmissionReportView(report.id(), report.projectId(), projectTitle, report.companyId(),
                report.period(), report.totalEnergyKwh(), report.totalCo2Kg(), report.vehicleCount(),
                report.status().name(), report.aiPreScore(), report.aiVersion(), report.aiPreNotes());
    }

}
