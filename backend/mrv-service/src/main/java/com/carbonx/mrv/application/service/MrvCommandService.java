package com.carbonx.mrv.application.service;

import com.carbonx.common.events.IntegrationEvents.EmissionReportApprovedEvent;
import com.carbonx.common.outbox.OutboxPort;
import com.carbonx.mrv.application.exception.MrvApplicationException;
import com.carbonx.mrv.application.exception.MrvErrorCode;
import com.carbonx.mrv.application.port.in.MrvCommandUseCase;
import com.carbonx.mrv.application.port.out.EmissionDatasetParserPort;
import com.carbonx.mrv.application.port.out.EmissionReportRepositoryPort;
import com.carbonx.mrv.application.port.out.FraudAnalysisRepositoryPort;
import com.carbonx.mrv.application.port.out.IdentityServiceClientPort;
import com.carbonx.mrv.application.port.out.ProjectApplicationRepositoryPort;
import com.carbonx.mrv.application.port.out.ProjectRepositoryPort;
import com.carbonx.mrv.application.port.out.SpringAiScoringPort;
import com.carbonx.mrv.application.port.out.SpringAiScoringPort.AiSuggestResult;
import com.carbonx.mrv.application.support.FraudSummary;
import com.carbonx.mrv.domain.fraud.FraudAssessment;
import com.carbonx.mrv.domain.fraud.FraudDetector;
import com.carbonx.mrv.domain.fraud.FraudFinding;
import com.carbonx.mrv.domain.project.ApplicationStatus;
import com.carbonx.mrv.domain.project.Project;
import com.carbonx.mrv.domain.project.ProjectApplication;
import com.carbonx.mrv.domain.report.EmissionDataset;
import com.carbonx.mrv.domain.report.EmissionReport;
import com.carbonx.mrv.domain.shared.StateTransitionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Command side of the MRV CQRS split. Every method runs in one local transaction; the
 * {@code EmissionReportApprovedEvent} is appended to the transactional outbox inside the same
 * transaction as the admin decision, so a Kafka outage can never lose a credit issuance.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class MrvCommandService implements MrvCommandUseCase {

    private static final int MAX_CSV_BYTES = 5 * 1024 * 1024;
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final List<ApplicationStatus> ACTIVE_APPLICATION_STATUSES =
            List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.CVA_APPROVED, ApplicationStatus.APPROVED);

    private final ProjectRepositoryPort projectRepository;
    private final ProjectApplicationRepositoryPort applicationRepository;
    private final EmissionReportRepositoryPort reportRepository;
    private final FraudAnalysisRepositoryPort fraudAnalysisRepository;
    private final IdentityServiceClientPort identityServiceClient;
    private final SpringAiScoringPort aiScoringPort;
    private final EmissionDatasetParserPort datasetParser;
    private final OutboxPort outbox;
    private final ObjectMapper objectMapper;
    private final FraudDetector fraudDetector = new FraudDetector();

    @Override
    public Long createProject(Long adminId, String title, String description, BigDecimal emissionFactor,
                              BigDecimal bufferRate, BigDecimal uncertaintyRate, BigDecimal leakageRate) {
        requireActor(adminId, "Admin");
        Project saved = projectRepository.save(Project.create(title, description, emissionFactor, bufferRate,
                uncertaintyRate, leakageRate));
        log.info("Project {} created by admin {}", saved.id(), adminId);
        return saved.id();
    }

    @Override
    public void updateProject(Long adminId, Long projectId, String title, String description, String status,
                              BigDecimal emissionFactor, BigDecimal bufferRate, BigDecimal uncertaintyRate,
                              BigDecimal leakageRate) {
        requireActor(adminId, "Admin");
        requireId(projectId, "projectId");
        Project existing = projectRepository.findById(projectId)
                .orElseThrow(() -> MrvApplicationException.notFound("Project not found: " + projectId));
        com.carbonx.mrv.domain.project.ProjectStatus nextStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                nextStatus = com.carbonx.mrv.domain.project.ProjectStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw MrvApplicationException.badRequest("Invalid project status: " + status);
            }
        }
        Project updated = projectRepository.save(existing.update(title, description, emissionFactor,
                bufferRate, uncertaintyRate, leakageRate, nextStatus));
        log.info("Project {} updated by admin {}", updated.id(), adminId);
    }

    @Override
    public void deleteProject(Long adminId, Long projectId) {
        requireActor(adminId, "Admin");
        requireId(projectId, "projectId");
        if (projectRepository.findById(projectId).isEmpty()) {
            throw MrvApplicationException.notFound("Project not found: " + projectId);
        }
        projectRepository.deleteById(projectId);
        log.info("Project {} deleted by admin {}", projectId, adminId);
    }

    @Override
    public Long submitApplication(Long companyId, Long projectId, String documentUrl) {
        requireActor(companyId, "Company");
        requireId(projectId, "projectId");
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> MrvApplicationException.notFound("Project not found: " + projectId));
        if (!project.isOpen()) {
            throw new StateTransitionException("Project " + projectId + " is closed for new applications");
        }
        if (applicationRepository.existsByStatuses(projectId, companyId, ACTIVE_APPLICATION_STATUSES)) {
            throw new MrvApplicationException(MrvErrorCode.DUPLICATE_APPLICATION,
                    "Company " + companyId + " already has an active application for project " + projectId);
        }
        // Identity is the authority on KYC: the snapshot stored here is display-only.
        String companyName = identityServiceClient.fetchApprovedCompanyName(companyId);
        ProjectApplication saved = applicationRepository.save(
                ProjectApplication.create(projectId, companyId, companyName, documentUrl));
        log.info("Application {} submitted by company {} for project {}", saved.id(), companyId, projectId);
        return saved.id();
    }

    @Override
    public void reviewApplicationByCva(Long cvaUserId, Long applicationId, String outcome, String note) {
        requireActor(cvaUserId, "CVA reviewer");
        requireId(applicationId, "applicationId");
        boolean approved = com.carbonx.mrv.application.support.ReviewOutcome.parse(outcome);
        ProjectApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> MrvApplicationException.notFound("Application not found: " + applicationId));
        ProjectApplication reviewed = applicationRepository.save(application.cvaReview(cvaUserId, approved, note));
        log.info("Application {} CVA-reviewed by {} status={}", reviewed.id(), cvaUserId, reviewed.status());
    }

    @Override
    public void reviewApplicationByAdmin(Long adminUserId, Long applicationId, String outcome, String note) {
        requireActor(adminUserId, "Admin reviewer");
        requireId(applicationId, "applicationId");
        boolean approved = com.carbonx.mrv.application.support.ReviewOutcome.parse(outcome);
        ProjectApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> MrvApplicationException.notFound("Application not found: " + applicationId));
        ProjectApplication reviewed = applicationRepository.save(application.adminReview(adminUserId, approved, note));
        log.info("Application {} admin-reviewed by {} status={}", reviewed.id(), adminUserId, reviewed.status());
    }

    @Override
    public Long uploadCsvReport(Long companyId, Long projectId, String originalFilename, InputStream csvStream) {
        requireActor(companyId, "Company");
        requireId(projectId, "projectId");
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> MrvApplicationException.notFound("Project not found: " + projectId));
        if (!project.isOpen()) {
            throw new StateTransitionException("Project " + projectId + " is closed for new reports");
        }
        ProjectApplication approved = applicationRepository.findApprovedByCompanyAndProject(companyId, projectId)
                .orElseThrow(() -> new StateTransitionException("Company " + companyId
                        + " has no approved application for project " + projectId));

        byte[] content = readAtMost(csvStream, MAX_CSV_BYTES);
        EmissionDataset dataset = datasetParser.parse(content);
        if (dataset.isEmpty()) {
            throw MrvApplicationException.badRequest("The uploaded CSV contains no data rows");
        }
        rejectForeignKeyPlates(companyId, dataset);

        BigDecimal energyKwh = dataset.totalEnergyKwh().setScale(4, RoundingMode.HALF_UP);
        BigDecimal co2Kg = project.grossCo2Kg(energyKwh).setScale(4, RoundingMode.HALF_UP);
        EmissionReport report = reportRepository.save(EmissionReport.create(approved.id(), project.id(), companyId,
                dataset.resolvedPeriod(), energyKwh, co2Kg, dataset.vehicleCount(), sha256(content),
                truncateFilename(originalFilename)));

        FraudAssessment assessment = fraudDetector.assess(dataset);
        fraudAnalysisRepository.replaceForReport(report.id(), assessment.findings());
        log.info("Report {} stored for company {} project {}: rows={} kwh={} co2Kg={} dqScore={}",
                report.id(), companyId, projectId, dataset.rows().size(), energyKwh, co2Kg,
                assessment.weightedScore());
        return report.id();
    }

    @Override
    public AiEvaluationView suggestAiScoreForCva(Long actorUserId, Long reportId) {
        requireActor(actorUserId, "CVA");
        requireId(reportId, "reportId");
        EmissionReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> MrvApplicationException.notFound("Report not found: " + reportId));
        if (!report.isAwaitingCva()) {
            throw new StateTransitionException(
                    "Report " + reportId + " is not awaiting CVA verification (status " + report.status() + ")");
        }
        List<FraudFinding> findings = fraudAnalysisRepository.findByReportId(reportId);
        if (findings.isEmpty()) {
            throw new MrvApplicationException(MrvErrorCode.REPORT_NOT_ANALYZED,
                    "Fraud analysis is missing for report " + reportId + "; re-upload the report before scoring");
        }
        String fraudSummaryJson = writeJson(FraudSummary.of(findings));
        AiSuggestResult suggestion = aiScoringPort.evaluateEmissionReport(report.id(), report.period(),
                report.totalEnergyKwh(), fraudSummaryJson);
        EmissionReport scored = reportRepository.save(
                report.withAiEvaluation(suggestion.aiPreScore(), suggestion.aiVersion(), suggestion.aiPreNotes()));
        log.info("Report {} pre-scored {} ({}) for CVA {}", reportId, scored.aiPreScore(), scored.aiVersion(),
                actorUserId);
        return new AiEvaluationView(scored.aiPreScore().doubleValue(), scored.aiVersion(), scored.aiPreNotes());
    }

    @Override
    public void verifyReportByCva(Long cvaUserId, Long reportId, boolean approved, String note) {
        requireActor(cvaUserId, "CVA verifier");
        requireId(reportId, "reportId");
        EmissionReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> MrvApplicationException.notFound("Report not found: " + reportId));
        EmissionReport verified = reportRepository.save(report.cvaVerify(cvaUserId, approved));
        // V1 has no CVA note column on emission_reports: the note stays in the audit log.
        log.info("Report {} CVA-verified by {} approved={} status={} note={}",
                verified.id(), cvaUserId, approved, verified.status(), note);
    }

    @Override
    public void approveReportByAdmin(Long adminUserId, Long reportId, boolean approved, String note) {
        requireActor(adminUserId, "Admin reviewer");
        requireId(reportId, "reportId");
        EmissionReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> MrvApplicationException.notFound("Report not found: " + reportId));
        EmissionReport decided = reportRepository.save(report.adminApprove(adminUserId, approved));
        log.info("Report {} admin decision by {} approved={} status={} note={}",
                decided.id(), adminUserId, approved, decided.status(), note);
        if (!approved) {
            return;
        }
        Project project = projectRepository.findById(decided.projectId())
                .orElseThrow(() -> MrvApplicationException.notFound("Project not found: " + decided.projectId()));
        outbox.append(new EmissionReportApprovedEvent(decided.id(), project.id(), project.title(),
                decided.companyId(), decided.period(), decided.totalEnergyKwh(), decided.totalCo2Kg(),
                project.emissionFactor(), project.bufferRate(), project.uncertaintyRate(), project.leakageRate()));
    }

    /**
     * Cross-checks the reported plates with identity-service. A plate mismatch is a hard rejection,
     * but an unavailable identity-service must not block an otherwise reviewable report: the CVA
     * still sees the DQ1..DQ8 findings, so the check degrades to a warning.
     */
    private void rejectForeignKeyPlates(Long companyId, EmissionDataset dataset) {
        List<String> plates = dataset.plates();
        if (plates.isEmpty()) {
            return;
        }
        List<String> verified;
        try {
            verified = identityServiceClient.verifyRegisteredPlates(companyId, plates);
        } catch (MrvApplicationException ex) {
            if (ex.code() != MrvErrorCode.SERVICE_UNAVAILABLE) {
                throw ex;
            }
            log.warn("identity-service unavailable; skipping plate cross-check for company {}", companyId);
            return;
        }
        if (verified != null && verified.isEmpty()) {
            throw MrvApplicationException.badRequest(
                    "None of the reported plate numbers is registered to company " + companyId);
        }
    }

    private String writeJson(FraudSummary summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialise the fraud summary", ex);
        }
    }

    private static byte[] readAtMost(InputStream stream, int maxBytes) {
        if (stream == null) {
            throw MrvApplicationException.badRequest("A CSV file is required");
        }
        try {
            byte[] content = stream.readNBytes(maxBytes + 1);
            if (content.length > maxBytes) {
                throw MrvApplicationException.badRequest("CSV file exceeds the " + maxBytes + " byte limit");
            }
            if (content.length == 0) {
                throw MrvApplicationException.badRequest("The uploaded CSV file is empty");
            }
            return content;
        } catch (IOException ex) {
            throw new MrvApplicationException(MrvErrorCode.BAD_REQUEST, "Cannot read the uploaded CSV file", ex);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable on this JVM", ex);
        }
    }

    private static String truncateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String trimmed = filename.trim();
        return trimmed.length() <= MAX_FILENAME_LENGTH ? trimmed : trimmed.substring(0, MAX_FILENAME_LENGTH);
    }

    private static void requireActor(Long actorId, String label) {
        if (actorId == null) {
            throw MrvApplicationException.forbidden(label + " identity is required");
        }
    }

    private static void requireId(Long id, String field) {
        if (id == null) {
            throw MrvApplicationException.badRequest(field + " is required");
        }
    }
}
