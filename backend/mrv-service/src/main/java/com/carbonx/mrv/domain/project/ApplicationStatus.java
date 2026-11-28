package com.carbonx.mrv.domain.project;

/** Two-level approval state machine: SUBMITTED -> CVA -> ADMIN. */
public enum ApplicationStatus {
    SUBMITTED,
    CVA_APPROVED,
    CVA_REJECTED,
    APPROVED,
    REJECTED;

    public boolean isFinal() {
        return this == CVA_REJECTED || this == APPROVED || this == REJECTED;
    }
}
