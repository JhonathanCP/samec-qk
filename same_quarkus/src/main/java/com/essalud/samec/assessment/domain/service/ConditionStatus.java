package com.essalud.samec.assessment.domain.service;

/** Where a condition stands within a self-assessment. */
public enum ConditionStatus {
    MET,
    NOT_MET,
    /** Evidence not uploaded or not yet reviewed, or on-site verification not done yet. */
    PENDING
}
