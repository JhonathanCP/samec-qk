package com.essalud.samec.catalog.domain.model;

/**
 * How a condition is verified. Only document review is backed by uploaded evidence; the other
 * techniques are checked on site and recorded as met / not met, with a comment and photos.
 */
public enum EvaluationTechnique {
    DOCUMENT_REVIEW(true),
    OBSERVATION(false),
    SAMPLING_MEDICAL_RECORD_AUDIT(false),
    INTERVIEW(false);

    private final boolean evidenceBased;

    EvaluationTechnique(boolean evidenceBased) {
        this.evidenceBased = evidenceBased;
    }

    public boolean isEvidenceBased() {
        return evidenceBased;
    }
}
