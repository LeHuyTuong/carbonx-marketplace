package com.carbonx.mrv.application.service;

import com.carbonx.mrv.application.exception.MrvApplicationException;
import com.carbonx.mrv.application.port.in.MrvQueryUseCase.EmissionReportView;
import com.carbonx.mrv.application.port.out.EmissionReportRepositoryPort;
import com.carbonx.mrv.application.port.out.ProjectApplicationRepositoryPort;
import com.carbonx.mrv.application.port.out.ProjectRepositoryPort;
import com.carbonx.mrv.application.port.out.SpringAiScoringPort;
import com.carbonx.mrv.domain.project.Project;
import com.carbonx.mrv.domain.project.ProjectStatus;
import com.carbonx.mrv.domain.report.EmissionReport;
import com.carbonx.mrv.domain.report.ReportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MrvQueryServiceTest {

    @Mock
    private EmissionReportRepositoryPort reportRepository;

    @Mock
    private ProjectRepositoryPort projectRepository;

    @Mock
    private ProjectApplicationRepositoryPort applicationRepository;

    @Mock
    private SpringAiScoringPort aiScoringPort;

    private MrvQueryService service;

    @BeforeEach
    void setUp() {
        service = new MrvQueryService(reportRepository, projectRepository, applicationRepository, aiScoringPort);
    }

    @Test
    @DisplayName("listMyReports joins the project title without leaking aggregates")
    void listMyReportsJoinsProjectTitle() {
        given(reportRepository.findByCompanyId(7L)).willReturn(List.of(report(31L, ReportStatus.SUBMITTED)));
        given(projectRepository.findAllByIds(anyList())).willReturn(List.of(project()));

        List<EmissionReportView> views = service.listMyReports(7L);

        assertThat(views).hasSize(1);
        EmissionReportView view = views.get(0);
        assertThat(view.id()).isEqualTo(31L);
        assertThat(view.projectTitle()).isEqualTo("EV fleet HCMC");
        assertThat(view.status()).isEqualTo("SUBMITTED");
        assertThat(view.totalEnergyKwh()).isEqualByComparingTo("300.0000");
    }

    @Test
    @DisplayName("An empty result never touches the project table")
    void emptyResultSkipsProjectLookup() {
        given(reportRepository.findByCompanyId(7L)).willReturn(List.of());

        assertThat(service.listMyReports(7L)).isEmpty();
        then(projectRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("The CVA queue only contains SUBMITTED reports")
    void cvaQueueFiltersSubmittedReports() {
        given(reportRepository.findByStatus(ReportStatus.SUBMITTED))
                .willReturn(List.of(report(31L, ReportStatus.SUBMITTED)));
        given(projectRepository.findAllByIds(anyList())).willReturn(List.of(project()));

        assertThat(service.listPendingReportsForCva()).hasSize(1);
        then(reportRepository).should().findByStatus(ReportStatus.SUBMITTED);
    }

    @Test
    @DisplayName("The admin view lists every report")
    void adminViewListsEverything() {
        given(reportRepository.findAll()).willReturn(List.of(
                report(31L, ReportStatus.APPROVED), report(32L, ReportStatus.REJECTED)));
        given(projectRepository.findAllByIds(anyList())).willReturn(List.of(project()));

        assertThat(service.listReportsForAdmin()).hasSize(2);
    }

    @Test
    @DisplayName("askCarbonAssistant forwards the role context and returns the assistant answer")
    void assistantForwardsRoleContext() {
        given(aiScoringPort.chatWithAssistant("role=ROLE_CVA; userId=2", "VM0038 là gì?"))
                .willReturn("VM0038 là phương pháp luận tính tín chỉ cho xe điện.");

        String answer = service.askCarbonAssistant(2L, "ROLE_CVA", "  VM0038 là gì?  ");

        assertThat(answer).contains("VM0038");
    }

    @Test
    @DisplayName("A blank chat message is rejected before the AI is called")
    void blankChatMessageIsRejected() {
        assertThatThrownBy(() -> service.askCarbonAssistant(2L, "ROLE_CVA", "   "))
                .isInstanceOf(MrvApplicationException.class);
        then(aiScoringPort).shouldHaveNoInteractions();
    }

    private static Project project() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        return new Project(11L, "EV fleet HCMC", null, new BigDecimal("0.6766"), new BigDecimal("0.05"),
                new BigDecimal("0.03"), new BigDecimal("0.02"), ProjectStatus.OPEN, now, now);
    }

    private static EmissionReport report(Long id, ReportStatus status) {
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        return new EmissionReport(id, 21L, 11L, 7L, "2026-09", new BigDecimal("300.0000"),
                new BigDecimal("202.9800"), 3, "a".repeat(64), "fleet.csv", status, null,
                null, null, null, now, now);
    }
}
