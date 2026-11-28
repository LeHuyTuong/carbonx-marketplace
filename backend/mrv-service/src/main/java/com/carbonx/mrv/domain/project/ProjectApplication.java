package com.carbonx.mrv.domain.project;

import com.carbonx.mrv.domain.shared.DomainValidationException;
import com.carbonx.mrv.domain.shared.StateTransitionException;

import java.time.Instant;

/**
 * A company's application to a project. The CVA reviews the submitted dossier first; only after a
 * CVA approval may an administrator take the final decision. A rejected application is final.
 */
public record ProjectApplication(
        Long id,
        Long projectId,
        Long companyId,
        String companyNameSnap,
        String documentUrl,
        ApplicationStatus status,
        Long cvaReviewerId,
        String cvaNote,
        Long adminReviewerId,
        String adminNote,
        Instant submittedAt,
        Instant updatedAt
) {

    private static final int MAX_NOTE_LENGTH = 2_000;

    public ProjectApplication {
        if (projectId == null) {
            throw new DomainValidationException("projectId is required");
        }
        if (companyId == null) {
            throw new DomainValidationException("companyId is required");
        }
        companyNameSnap = trimToNull(companyNameSnap);
        documentUrl = trimToNull(documentUrl);
        status = status == null ? ApplicationStatus.SUBMITTED : status;
        submittedAt = submittedAt == null ? Instant.now() : submittedAt;
        updatedAt = updatedAt == null ? submittedAt : updatedAt;
    }

    public static ProjectApplication create(Long projectId, Long companyId, String companyNameSnap,
                                            String documentUrl) {
        Instant now = Instant.now();
        return new ProjectApplication(null, projectId, companyId, companyNameSnap, documentUrl,
                ApplicationStatus.SUBMITTED, null, null, null, null, now, now);
    }

    /** First approval level (CVA). */
    public ProjectApplication cvaReview(Long reviewerId, boolean approved, String note) {
        if (status != ApplicationStatus.SUBMITTED) {
            throw new StateTransitionException(
                    "Application " + id + " is not awaiting CVA review (status " + status + ")");
        }
        requireReviewer(reviewerId, "CVA reviewer");
        return new ProjectApplication(id, projectId, companyId, companyNameSnap, documentUrl,
                approved ? ApplicationStatus.CVA_APPROVED : ApplicationStatus.CVA_REJECTED,
                reviewerId, note(note), adminReviewerId, adminNote, submittedAt, Instant.now());
    }

    /** Second and final approval level (ADMIN); unreachable before a CVA approval. */
    public ProjectApplication adminReview(Long reviewerId, boolean approved, String note) {
        if (status != ApplicationStatus.CVA_APPROVED) {
            throw new StateTransitionException(
                    "Application " + id + " must be CVA-approved before the admin decision (status " + status + ")");
        }
        requireReviewer(reviewerId, "Admin reviewer");
        return new ProjectApplication(id, projectId, companyId, companyNameSnap, documentUrl,
                approved ? ApplicationStatus.APPROVED : ApplicationStatus.REJECTED,
                cvaReviewerId, cvaNote, reviewerId, note(note), submittedAt, Instant.now());
    }

    public boolean isPending() {
        return !status.isFinal();
    }

    private static void requireReviewer(Long reviewerId, String label) {
        if (reviewerId == null) {
            throw new DomainValidationException(label + " is required");
        }
    }

    private static String note(String note) {
        String trimmed = trimToNull(note);
        if (trimmed != null && trimmed.length() > MAX_NOTE_LENGTH) {
            throw new DomainValidationException("Review note must be at most " + MAX_NOTE_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
