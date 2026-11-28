package com.carbonx.mrv.application.service;

import com.carbonx.common.events.IntegrationAggregateType;
import com.carbonx.common.events.IntegrationEventType;
import com.carbonx.common.events.IntegrationEvents.EmissionReportApprovedEvent;
import com.carbonx.common.outbox.OutboxPort;
import com.carbonx.mrv.application.exception.MrvApplicationException;
import com.carbonx.mrv.application.exception.MrvErrorCode;
import com.carbonx.mrv.application.port.in.MrvCommandUseCase.AiEvaluationView;
import com.carbonx.mrv.application.port.out.EmissionReportRepositoryPort;
import com.carbonx.mrv.application.port.out.FraudAnalysisRepositoryPort;
import com.carbonx.mrv.application.port.out.IdentityServiceClientPort;
import com.carbonx.mrv.application.port.out.ProjectApplicationRepositoryPort;
import com.carbonx.mrv.application.port.out.ProjectRepositoryPort;
import com.carbonx.mrv.application.port.out.SpringAiScoringPort;
import com.carbonx.mrv.application.port.out.SpringAiScoringPort.AiSuggestResult;
import com.carbonx.mrv.domain.fraud.FraudDetector;
import com.carbonx.mrv.domain.fraud.FraudFinding;
import com.carbonx.mrv.domain.project.ApplicationStatus;
import com.carbonx.mrv.domain.project.Project;
import com.carbonx.mrv.domain.project.ProjectApplication;
import com.carbonx.mrv.domain.project.ProjectStatus;
import com.carbonx.mrv.domain.report.EmissionDataset;
import com.carbonx.mrv.domain.report.EmissionReport;
import com.carbonx.mrv.domain.report.ReportStatus;
import com.carbonx.mrv.domain.shared.StateTransitionException;
import com.carbonx.mrv.infrastructure.parser.CsvEmissionDatasetParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MrvCommandServiceTest {

    private static final long COMPANY_ID = 7L;
    private static final long PROJECT_ID = 11L;
    private static final long APPLICATION_ID = 21L;
    private static final long REPORT_ID = 31L;

    @Mock
    private ProjectRepositoryPort projectRepository;

    @Mock
    private ProjectApplicationRepositoryPort applicationRepository;

    @Mock
    private EmissionReportRepositoryPort reportRepository;

    @Mock
    private FraudAnalysisRepositoryPort fraudAnalysisRepository;

    @Mock
    private IdentityServiceClientPort identityServiceClient;

    @Mock
    private SpringAiScoringPort aiScoringPort;

    @Mock
    private OutboxPort outbox;

    private MrvCommandService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new MrvCommandService(projectRepository, applicationRepository, reportRepository,
                fraudAnalysisRepository, identityServiceClient, aiScoringPort,
                new CsvEmissionDatasetParser(), outbox, objectMapper);
    }

    @Test
    @DisplayName("createProject applies the VM0038 defaults and returns the new id")
    void createProjectAppliesDefaults() {
        given(projectRepository.save(any(Project.class))).willAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            return new Project(13L, project.title(), project.description(), project.emissionFactor(),
                    project.bufferRate(), project.uncertaintyRate(), project.leakageRate(), project.status(),
                    project.createdAt(), project.updatedAt());
        });

        Long id = service.createProject(1L, "EV fleet HCMC", null, null, null, null, null);

        assertThat(id).isEqualTo(13L);
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        then(projectRepository).should().save(captor.capture());
        Project saved = captor.getValue();
        assertThat(saved.title()).isEqualTo("EV fleet HCMC");
        assertThat(saved.emissionFactor()).isEqualByComparingTo("0.676600");
        assertThat(saved.bufferRate()).isEqualByComparingTo("0.0500");
        assertThat(saved.uncertaintyRate()).isEqualByComparingTo("0.0300");
        assertThat(saved.leakageRate()).isEqualByComparingTo("0.0200");
        assertThat(saved.status()).isEqualTo(ProjectStatus.OPEN);
    }

    @Test
    @DisplayName("submitApplication snapshots the KYC-approved company name from identity-service")
    void submitApplicationSnapshotsCompanyName() {
        given(projectRepository.findById(PROJECT_ID)).willReturn(Optional.of(project()));
        given(applicationRepository.existsByStatuses(eq(PROJECT_ID), eq(COMPANY_ID), anyList())).willReturn(false);
        given(identityServiceClient.fetchApprovedCompanyName(COMPANY_ID)).willReturn("Acme Logistics JSC");
        given(applicationRepository.save(any(ProjectApplication.class))).willAnswer(invocation -> {
            ProjectApplication draft = invocation.getArgument(0);
            return withId(draft, APPLICATION_ID);
        });

        Long id = service.submitApplication(COMPANY_ID, PROJECT_ID, "https://files/dossier.pdf");

        assertThat(id).isEqualTo(APPLICATION_ID);
        ArgumentCaptor<ProjectApplication> captor = ArgumentCaptor.forClass(ProjectApplication.class);
        then(applicationRepository).should().save(captor.capture());
        assertThat(captor.getValue().companyNameSnap()).isEqualTo("Acme Logistics JSC");
        assertThat(captor.getValue().status()).isEqualTo(ApplicationStatus.SUBMITTED);
    }

    @Test
    @DisplayName("A second active application for the same project is rejected with 409")
    void duplicateApplicationIsRejected() {
        given(projectRepository.findById(PROJECT_ID)).willReturn(Optional.of(project()));
        given(applicationRepository.existsByStatuses(eq(PROJECT_ID), eq(COMPANY_ID), anyList())).willReturn(true);

        assertThatThrownBy(() -> service.submitApplication(COMPANY_ID, PROJECT_ID, null))
                .isInstanceOf(MrvApplicationException.class)
                .extracting(ex -> ((MrvApplicationException) ex).code())
                .isEqualTo(MrvErrorCode.DUPLICATE_APPLICATION);

        then(applicationRepository).should(never()).save(any());
        then(identityServiceClient).should(never()).fetchApprovedCompanyName(anyLong());
    }

    @Test
    @DisplayName("The CVA cannot review an application twice")
    void cvaReviewRejectsSecondDecision() {
        ProjectApplication alreadyReviewed = withStatus(application(), ApplicationStatus.CVA_APPROVED);
        given(applicationRepository.findById(APPLICATION_ID)).willReturn(Optional.of(alreadyReviewed));

        assertThatThrownBy(() -> service.reviewApplicationByCva(2L, APPLICATION_ID, "APPROVE", "ok"))
                .isInstanceOf(StateTransitionException.class)
                .hasMessageContaining("CVA");
        then(applicationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("The admin decision is unreachable before the CVA approval")
    void adminDecisionRequiresCvaApproval() {
        given(applicationRepository.findById(APPLICATION_ID)).willReturn(Optional.of(application()));

        assertThatThrownBy(() -> service.reviewApplicationByAdmin(1L, APPLICATION_ID, "APPROVE", "ok"))
                .isInstanceOf(StateTransitionException.class)
                .hasMessageContaining("CVA-approved");
        then(applicationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("CVA approval then admin approval walks the full two-level state machine")
    void twoLevelApprovalWalksTheStateMachine() {
        given(applicationRepository.findById(APPLICATION_ID)).willReturn(Optional.of(application()));
        given(applicationRepository.save(any(ProjectApplication.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.reviewApplicationByCva(2L, APPLICATION_ID, "APPROVE", "dossier ok");
        ProjectApplication cvaApproved = withStatus(application(), ApplicationStatus.CVA_APPROVED);
        given(applicationRepository.findById(APPLICATION_ID)).willReturn(Optional.of(cvaApproved));
        service.reviewApplicationByAdmin(1L, APPLICATION_ID, "approve", "final ok");

        ArgumentCaptor<ProjectApplication> captor = ArgumentCaptor.forClass(ProjectApplication.class);
        then(applicationRepository).should(org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).status()).isEqualTo(ApplicationStatus.CVA_APPROVED);
        assertThat(captor.getAllValues().get(0).cvaNote()).isEqualTo("dossier ok");
        assertThat(captor.getAllValues().get(1).status()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(captor.getAllValues().get(1).adminReviewerId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("uploadCsvReport computes totals, hashes the file and persists the eight DQ findings")
    void uploadComputesTotalsAndPersistsFraudAnalysis() {
        given(projectRepository.findById(PROJECT_ID)).willReturn(Optional.of(project()));
        given(applicationRepository.findApprovedByCompanyAndProject(COMPANY_ID, PROJECT_ID))
                .willReturn(Optional.of(approvedApplication()));
        given(identityServiceClient.verifyRegisteredPlates(eq(COMPANY_ID), anyList()))
                .willReturn(List.of("51A12345", "51A23456", "51A34567"));
        given(reportRepository.save(any(EmissionReport.class))).willAnswer(invocation -> {
            EmissionReport draft = invocation.getArgument(0);
            return withId(draft, REPORT_ID);
        });

        Long reportId = service.uploadCsvReport(COMPANY_ID, PROJECT_ID, "fleet-2026-09.csv", csv(
                "2026-09,100.0,51A12345",
                "2026-09,120.5,51A23456",
                "2026-09,79.5,51A34567"));

        assertThat(reportId).isEqualTo(REPORT_ID);
        ArgumentCaptor<EmissionReport> reportCaptor = ArgumentCaptor.forClass(EmissionReport.class);
        then(reportRepository).should().save(reportCaptor.capture());
        EmissionReport saved = reportCaptor.getValue();
        assertThat(saved.totalEnergyKwh()).isEqualByComparingTo("300.0000");
        assertThat(saved.totalCo2Kg()).isEqualByComparingTo("202.9800");
        assertThat(saved.vehicleCount()).isEqualTo(3);
        assertThat(saved.period()).isEqualTo("2026-09");
        assertThat(saved.fileSha256()).hasSize(64);
        assertThat(saved.status()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(saved.applicationId()).isEqualTo(APPLICATION_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FraudFinding>> findingsCaptor = ArgumentCaptor.forClass(List.class);
        then(fraudAnalysisRepository).should().replaceForReport(eq(REPORT_ID), findingsCaptor.capture());
        assertThat(findingsCaptor.getValue()).hasSize(8);
        assertThat(findingsCaptor.getValue()).extracting(FraudFinding::ruleCode)
                .containsExactly("DQ1_SCHEMA", "DQ2_PERIOD", "DQ3_ENERGY", "DQ4_DUP_PLATE",
                        "DQ5_DUP_ROW", "DQ6_OUTLIER_IQR", "DQ7_UNIFORMITY_CV", "DQ8_REPEATED_ROUNDED");
    }

    @Test
    @DisplayName("Plates that do not belong to the company are rejected before anything is stored")
    void uploadRejectsUnregisteredPlates() {
        given(projectRepository.findById(PROJECT_ID)).willReturn(Optional.of(project()));
        given(applicationRepository.findApprovedByCompanyAndProject(COMPANY_ID, PROJECT_ID))
                .willReturn(Optional.of(approvedApplication()));
        given(identityServiceClient.verifyRegisteredPlates(eq(COMPANY_ID), anyList())).willReturn(List.of());

        assertThatThrownBy(() -> service.uploadCsvReport(COMPANY_ID, PROJECT_ID, "x.csv",
                csv("2026-09,100.0,51A99999")))
                .isInstanceOf(MrvApplicationException.class)
                .extracting(ex -> ((MrvApplicationException) ex).code())
                .isEqualTo(MrvErrorCode.BAD_REQUEST);
        then(reportRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("An identity outage degrades the plate cross-check instead of blocking the upload")
    void uploadDegradesWhenIdentityIsUnavailable() {
        given(projectRepository.findById(PROJECT_ID)).willReturn(Optional.of(project()));
        given(applicationRepository.findApprovedByCompanyAndProject(COMPANY_ID, PROJECT_ID))
                .willReturn(Optional.of(approvedApplication()));
        given(identityServiceClient.verifyRegisteredPlates(eq(COMPANY_ID), anyList()))
                .willThrow(new MrvApplicationException(MrvErrorCode.SERVICE_UNAVAILABLE, "down"));
        given(reportRepository.save(any(EmissionReport.class))).willAnswer(invocation -> {
            EmissionReport draft = invocation.getArgument(0);
            return withId(draft, REPORT_ID);
        });

        Long reportId = service.uploadCsvReport(COMPANY_ID, PROJECT_ID, "x.csv",
                csv("2026-09,100.0,51A12345"));

        assertThat(reportId).isEqualTo(REPORT_ID);
        then(reportRepository).should().save(any(EmissionReport.class));
    }

    @Test
    @DisplayName("Reporting requires an APPROVED project application")
    void uploadRequiresApprovedApplication() {
        given(projectRepository.findById(PROJECT_ID)).willReturn(Optional.of(project()));
        given(applicationRepository.findApprovedByCompanyAndProject(COMPANY_ID, PROJECT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploadCsvReport(COMPANY_ID, PROJECT_ID, "x.csv",
                csv("2026-09,100.0,51A12345")))
                .isInstanceOf(StateTransitionException.class)
                .hasMessageContaining("no approved application");
    }

    @Test
    @DisplayName("AI pre-scoring persists the score and returns it to the CVA")
    void aiScoringPersistsSuggestion() {
        EmissionReport report = withId(submittedReport(), REPORT_ID);
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));
        given(fraudAnalysisRepository.findByReportId(REPORT_ID)).willReturn(findings());
        given(aiScoringPort.evaluateEmissionReport(eq(REPORT_ID), eq("2026-09"), any(), any()))
                .willReturn(new AiSuggestResult(new BigDecimal("91.25"), "spring-ai-openai", "Dữ liệu tốt"));
        given(reportRepository.save(any(EmissionReport.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        AiEvaluationView view = service.suggestAiScoreForCva(2L, REPORT_ID);

        assertThat(view.aiPreScore()).isEqualTo(91.25);
        assertThat(view.aiVersion()).isEqualTo("spring-ai-openai");
        ArgumentCaptor<EmissionReport> captor = ArgumentCaptor.forClass(EmissionReport.class);
        then(reportRepository).should().save(captor.capture());
        assertThat(captor.getValue().aiPreScore()).isEqualByComparingTo("91.25");
    }

    @Test
    @DisplayName("Pre-scoring without a stored fraud analysis is a conflict, never an LLM call")
    void aiScoringRequiresStoredFindings() {
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(withId(submittedReport(), REPORT_ID)));
        given(fraudAnalysisRepository.findByReportId(REPORT_ID)).willReturn(List.of());

        assertThatThrownBy(() -> service.suggestAiScoreForCva(2L, REPORT_ID))
                .isInstanceOf(MrvApplicationException.class)
                .extracting(ex -> ((MrvApplicationException) ex).code())
                .isEqualTo(MrvErrorCode.REPORT_NOT_ANALYZED);
        then(aiScoringPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("A CVA-verified report can be verified but not verified twice")
    void cvaVerificationIsSingleUse() {
        EmissionReport report = withStatus(submittedReport(), ReportStatus.CVA_REJECTED);
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));

        assertThatThrownBy(() -> service.verifyReportByCva(2L, REPORT_ID, true, "late"))
                .isInstanceOf(StateTransitionException.class);
    }

    @Test
    @DisplayName("Admin approval appends EmissionReportApprovedEvent to the outbox in the same transaction")
    void adminApprovalPublishesEventThroughOutbox() {
        given(reportRepository.findById(REPORT_ID))
                .willReturn(Optional.of(withStatus(withId(submittedReport(), REPORT_ID), ReportStatus.CVA_VERIFIED)));
        given(reportRepository.save(any(EmissionReport.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(projectRepository.findById(PROJECT_ID)).willReturn(Optional.of(project()));

        service.approveReportByAdmin(1L, REPORT_ID, true, "approved");

        ArgumentCaptor<com.carbonx.common.domain.DomainEvent> captor =
                ArgumentCaptor.forClass(com.carbonx.common.domain.DomainEvent.class);
        then(outbox).should().append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(EmissionReportApprovedEvent.class);
        EmissionReportApprovedEvent event = (EmissionReportApprovedEvent) captor.getValue();
        assertThat(event.reportId()).isEqualTo(REPORT_ID);
        assertThat(event.projectTitle()).isEqualTo("EV fleet HCMC");
        assertThat(event.companyId()).isEqualTo(COMPANY_ID);
        assertThat(event.emissionFactor()).isEqualByComparingTo("0.6766");
        assertThat(event.bufferRate()).isEqualByComparingTo("0.0500");
        assertThat(event.eventType())
                .isEqualTo(IntegrationEventType.MRV_EMISSION_REPORT_APPROVED.wireName());
        assertThat(event.aggregateType())
                .isEqualTo(IntegrationAggregateType.EMISSION_REPORT.wireName());
    }

    @Test
    @DisplayName("Admin rejection never publishes a credit-issuance event")
    void adminRejectionDoesNotPublishEvent() {
        given(reportRepository.findById(REPORT_ID))
                .willReturn(Optional.of(withStatus(submittedReport(), ReportStatus.CVA_VERIFIED)));
        given(reportRepository.save(any(EmissionReport.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.approveReportByAdmin(1L, REPORT_ID, false, "energy evidence missing");

        then(outbox).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("The admin cannot decide before the CVA verification")
    void adminApprovalRequiresCvaVerification() {
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(withId(submittedReport(), REPORT_ID)));

        assertThatThrownBy(() -> service.approveReportByAdmin(1L, REPORT_ID, true, "skip"))
                .isInstanceOf(StateTransitionException.class)
                .hasMessageContaining("CVA-verified");
        then(outbox).shouldHaveNoInteractions();
    }

    private static ByteArrayInputStream csv(String... rows) {
        StringBuilder body = new StringBuilder("period,total_energy,license_plate\n");
        for (String row : rows) {
            body.append(row).append('\n');
        }
        return new ByteArrayInputStream(body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Project project() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        return new Project(PROJECT_ID, "EV fleet HCMC", "Fleet electrification", new BigDecimal("0.6766"),
                new BigDecimal("0.0500"), new BigDecimal("0.0300"), new BigDecimal("0.0200"),
                ProjectStatus.OPEN, now, now);
    }

    private static ProjectApplication application() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        return new ProjectApplication(APPLICATION_ID, PROJECT_ID, COMPANY_ID, "Acme Logistics",
                "https://files/dossier.pdf", ApplicationStatus.SUBMITTED, null, null, null, null, now, now);
    }

    private static ProjectApplication approvedApplication() {
        Instant now = Instant.parse("2026-09-02T00:00:00Z");
        return new ProjectApplication(APPLICATION_ID, PROJECT_ID, COMPANY_ID, "Acme Logistics",
                "https://files/dossier.pdf", ApplicationStatus.APPROVED, 2L, "ok", 1L, "ok", now, now);
    }

    private static EmissionReport submittedReport() {
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        return new EmissionReport(null, APPLICATION_ID, PROJECT_ID, COMPANY_ID, "2026-09",
                new BigDecimal("300.0000"), new BigDecimal("202.9800"), 3, "a".repeat(64),
                "fleet-2026-09.csv", ReportStatus.SUBMITTED, null, null, null, null, now, now);
    }

    private static List<FraudFinding> findings() {
        EmissionDataset dataset = new EmissionDataset(
                List.of(EmissionDataset.COL_PERIOD, EmissionDataset.COL_ENERGY, EmissionDataset.COL_PLATE),
                List.of(
                        new com.carbonx.mrv.domain.report.EmissionRow("2026-09", "100.0", "51A12345"),
                        new com.carbonx.mrv.domain.report.EmissionRow("2026-09", "120.5", "51A23456"),
                        new com.carbonx.mrv.domain.report.EmissionRow("2026-09", "79.5", "51A34567")));
        return new FraudDetector().assess(dataset).findings();
    }

    private static ProjectApplication withStatus(ProjectApplication application, ApplicationStatus status) {
        return new ProjectApplication(application.id(), application.projectId(), application.companyId(),
                application.companyNameSnap(), application.documentUrl(), status, application.cvaReviewerId(),
                application.cvaNote(), application.adminReviewerId(), application.adminNote(),
                application.submittedAt(), application.updatedAt());
    }

    private static EmissionReport withStatus(EmissionReport report, ReportStatus status) {
        return new EmissionReport(report.id(), report.applicationId(), report.projectId(), report.companyId(),
                report.period(), report.totalEnergyKwh(), report.totalCo2Kg(), report.vehicleCount(),
                report.fileSha256(), report.originalFilename(), status, report.verifierId(),
                report.aiPreScore(), report.aiVersion(), report.aiPreNotes(), report.submittedAt(),
                report.updatedAt());
    }

    private static ProjectApplication withId(ProjectApplication application, Long id) {
        return new ProjectApplication(id, application.projectId(), application.companyId(),
                application.companyNameSnap(), application.documentUrl(), application.status(),
                application.cvaReviewerId(), application.cvaNote(), application.adminReviewerId(),
                application.adminNote(), application.submittedAt(), application.updatedAt());
    }

    private static EmissionReport withId(EmissionReport report, Long id) {
        return new EmissionReport(id, report.applicationId(), report.projectId(), report.companyId(),
                report.period(), report.totalEnergyKwh(), report.totalCo2Kg(), report.vehicleCount(),
                report.fileSha256(), report.originalFilename(), report.status(), report.verifierId(),
                report.aiPreScore(), report.aiVersion(), report.aiPreNotes(), report.submittedAt(),
                report.updatedAt());
    }
}
