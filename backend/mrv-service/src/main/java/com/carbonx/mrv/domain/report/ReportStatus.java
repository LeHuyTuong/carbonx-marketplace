package com.carbonx.mrv.domain.report;

/** Two-level verification state machine: SUBMITTED -> CVA -> ADMIN. */
public enum ReportStatus {
    SUBMITTED,
    CVA_VERIFIED,
    CVA_REJECTED,
    APPROVED,
    REJECTED;

    public boolean isFinal() {
        return this == CVA_REJECTED || this == APPROVED || this == REJECTED;
    }
}
