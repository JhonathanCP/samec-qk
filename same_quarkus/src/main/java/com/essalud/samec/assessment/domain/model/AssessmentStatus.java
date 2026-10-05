package com.essalud.samec.assessment.domain.model;

public enum AssessmentStatus {
    /** Evidence can be uploaded and reviewed. */
    OPEN,
    /** Final score computed; nothing else can change. */
    CLOSED
}
