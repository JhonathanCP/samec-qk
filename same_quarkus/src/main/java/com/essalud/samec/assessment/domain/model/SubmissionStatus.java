package com.essalud.samec.assessment.domain.model;

public enum SubmissionStatus {
    /** Waiting for the evaluator. */
    PENDING_REVIEW,
    APPROVED,
    /** Returned to the facility; it must correct it and submit again. */
    OBSERVED
}
