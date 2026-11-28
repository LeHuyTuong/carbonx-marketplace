package com.carbonx.mrv.api.dto;

import com.carbonx.mrv.application.port.in.MrvCommandUseCase.AiEvaluationView;
import com.carbonx.mrv.application.port.in.MrvQueryUseCase.EmissionReportView;
import com.carbonx.mrv.application.port.in.MrvQueryUseCase.ProjectApplicationView;
import com.carbonx.mrv.application.port.in.MrvQueryUseCase.ProjectView;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Project, application, report and assistant request/response contracts. */
public final class MrvDtos {

    private MrvDtos() {
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 4000) String description,
            @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 6, fraction = 6) BigDecimal emissionFactor,
            @DecimalMin("0.0") @DecimalMax("1.0") @Digits(integer = 1, fraction = 4) BigDecimal bufferRate,
            @DecimalMin("0.0") @DecimalMax("1.0") @Digits(integer = 1, fraction = 4) BigDecimal uncertaintyRate,
            @DecimalMin("0.0") @DecimalMax("1.0") @Digits(integer = 1, fraction = 4) BigDecimal leakageRate) {
    }

    public record UpdateProjectRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 4000) String description,
            @Size(max = 32) String status,
            @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 6, fraction = 6) BigDecimal emissionFactor,
            @DecimalMin("0.0") @DecimalMax("1.0") @Digits(integer = 1, fraction = 4) BigDecimal bufferRate,
            @DecimalMin("0.0") @DecimalMax("1.0") @Digits(integer = 1, fraction = 4) BigDecimal uncertaintyRate,
            @DecimalMin("0.0") @DecimalMax("1.0") @Digits(integer = 1, fraction = 4) BigDecimal leakageRate) {
    }

    public record ProjectResponse(
            Long id, String title, String description, String status,
            BigDecimal emissionFactor, BigDecimal bufferRate, BigDecimal uncertaintyRate,
            BigDecimal leakageRate, Instant createdAt) {

        public static ProjectResponse from(ProjectView view) {
            return new ProjectResponse(view.id(), view.title(), view.description(), view.status(),
                    view.emissionFactor(), view.bufferRate(), view.uncertaintyRate(),
                    view.leakageRate(), view.createdAt());
        }
    }

    public record ProjectApplicationResponse(
            Long id, Long projectId, String projectTitle, Long companyId,
            String companyName, String documentUrl, String status,
            Long cvaReviewerId, String cvaNote, Long adminReviewerId, String adminNote,
            Instant submittedAt, Instant updatedAt) {

        public static ProjectApplicationResponse from(ProjectApplicationView view) {
            return new ProjectApplicationResponse(view.id(), view.projectId(), view.projectTitle(),
                    view.companyId(), view.companyNameSnap(), view.documentUrl(), view.status(),
                    view.cvaReviewerId(), view.cvaNote(), view.adminReviewerId(), view.adminNote(),
                    view.submittedAt(), view.updatedAt());
        }
    }

    public record IdResponse(Long id) {
    }

    public record SubmitApplicationRequest(
            @NotNull Long projectId,
            @Size(max = 500) String documentUrl) {
    }

    public record ReviewRequest(
            @NotBlank @Size(max = 32) String outcome,
            @Size(max = 2000) String note) {
    }

    public record VerifyReportRequest(
            boolean approved,
            @Size(max = 2000) String note) {
    }

    public record AiScoreResponse(Double aiPreScore, String aiVersion, String aiPreNotes) {
        public static AiScoreResponse from(AiEvaluationView view) {
            return new AiScoreResponse(view.aiPreScore(), view.aiVersion(), view.aiPreNotes());
        }
    }

    public record EmissionReportResponse(
            Long id, Long projectId, String projectTitle, Long companyId, String period,
            BigDecimal totalEnergyKwh, BigDecimal totalCo2Kg, int vehicleCount, String status,
            BigDecimal aiPreScore, String aiVersion, String aiPreNotes) {

        public static EmissionReportResponse from(EmissionReportView view) {
            return new EmissionReportResponse(view.id(), view.projectId(), view.projectTitle(), view.companyId(),
                    view.period(), view.totalEnergyKwh(), view.totalCo2Kg(), view.vehicleCount(), view.status(),
                    view.aiPreScore(), view.aiVersion(), view.aiPreNotes());
        }
    }

    public record ChatRequest(@NotBlank @Size(max = 2000) String message) {
    }

    public record ChatResponse(String answer) {
    }
}
