package com.carbonx.mrv.domain.report;

import com.carbonx.mrv.domain.shared.DomainValidationException;
import com.carbonx.mrv.domain.shared.StateTransitionException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Emission data submitted by a company for an approved project. The CVA verifies the data (with an
 * optional AI pre-score) and only then may an administrator approve it, which publishes
 * {@code EmissionReportApprovedEvent} through the transactional outbox.
 */
public record EmissionReport(
        Long id,
        Long applicationId,
        Long projectId,
        Long companyId,
        String period,
        BigDecimal totalEnergyKwh,
        BigDecimal totalCo2Kg,
        int vehicleCount,
        String fileSha256,
        String originalFilename,
        ReportStatus status,
        Long verifierId,
        BigDecimal aiPreScore,
        String aiVersion,
        String aiPreNotes,
        Instant submittedAt,
        Instant updatedAt
) {

    public static final int MIN_AI_SCORE = 0;
    public static final int MAX_AI_SCORE = 100;
    private static final int MAX_NOTES_LENGTH = 4_000;

    public EmissionReport {
        if (applicationId == null) {
            throw new DomainValidationException("applicationId is required");
        }
        if (projectId == null) {
            throw new DomainValidationException("projectId is required");
        }
        if (companyId == null) {
            throw new DomainValidationException("companyId is required");
        }
        if (period == null || period.isBlank()) {
            throw new DomainValidationException("Reporting period is required");
        }
        period = period.trim();
        if (totalEnergyKwh == null || totalEnergyKwh.signum() < 0) {
            throw new DomainValidationException("totalEnergyKwh must be zero or positive");
        }
        if (totalCo2Kg == null || totalCo2Kg.signum() < 0) {
            throw new DomainValidationException("totalCo2Kg must be zero or positive");
        }
        if (vehicleCount < 0) {
            throw new DomainValidationException("vehicleCount must be zero or positive");
        }
        status = status == null ? ReportStatus.SUBMITTED : status;
        submittedAt = submittedAt == null ? Instant.now() : submittedAt;
        updatedAt = updatedAt == null ? submittedAt : updatedAt;
    }

    public static EmissionReport create(Long applicationId, Long projectId, Long companyId, String period,
                                        BigDecimal totalEnergyKwh, BigDecimal totalCo2Kg, int vehicleCount,
                                        String fileSha256, String originalFilename) {
        Instant now = Instant.now();
        return new EmissionReport(null, applicationId, projectId, companyId, period, totalEnergyKwh, totalCo2Kg,
                vehicleCount, fileSha256, originalFilename, ReportStatus.SUBMITTED, null, null, null, null, now, now);
    }

    /** Persists the AI (or DQ fallback) pre-score produced for the CVA. */
    public EmissionReport withAiEvaluation(BigDecimal score, String version, String notes) {
        if (score == null) {
            throw new DomainValidationException("aiPreScore is required");
        }
        BigDecimal clamped = score.max(BigDecimal.valueOf(MIN_AI_SCORE))
                .min(BigDecimal.valueOf(MAX_AI_SCORE))
                .setScale(2, RoundingMode.HALF_UP);
        String trimmedNotes = notes == null ? null : notes.trim();
        if (trimmedNotes != null && trimmedNotes.length() > MAX_NOTES_LENGTH) {
            trimmedNotes = trimmedNotes.substring(0, MAX_NOTES_LENGTH);
        }
        return new EmissionReport(id, applicationId, projectId, companyId, period, totalEnergyKwh, totalCo2Kg,
                vehicleCount, fileSha256, originalFilename, status, verifierId, clamped, version, trimmedNotes,
                submittedAt, Instant.now());
    }

    /** First verification level (CVA). */
    public EmissionReport cvaVerify(Long actorId, boolean approved) {
        if (status != ReportStatus.SUBMITTED) {
            throw new StateTransitionException(
                    "Report " + id + " is not awaiting CVA verification (status " + status + ")");
        }
        if (actorId == null) {
            throw new DomainValidationException("CVA verifier is required");
        }
        return new EmissionReport(id, applicationId, projectId, companyId, period, totalEnergyKwh, totalCo2Kg,
                vehicleCount, fileSha256, originalFilename,
                approved ? ReportStatus.CVA_VERIFIED : ReportStatus.CVA_REJECTED,
                actorId, aiPreScore, aiVersion, aiPreNotes, submittedAt, Instant.now());
    }

    /** Second and final verification level (ADMIN); unreachable before a CVA verification. */
    public EmissionReport adminApprove(Long actorId, boolean approved) {
        if (status != ReportStatus.CVA_VERIFIED) {
            throw new StateTransitionException(
                    "Report " + id + " must be CVA-verified before the admin decision (status " + status + ")");
        }
        if (actorId == null) {
            throw new DomainValidationException("Admin reviewer is required");
        }
        return new EmissionReport(id, applicationId, projectId, companyId, period, totalEnergyKwh, totalCo2Kg,
                vehicleCount, fileSha256, originalFilename,
                approved ? ReportStatus.APPROVED : ReportStatus.REJECTED,
                verifierId, aiPreScore, aiVersion, aiPreNotes, submittedAt, Instant.now());
    }

    public boolean isAwaitingCva() {
        return status == ReportStatus.SUBMITTED;
    }
}
