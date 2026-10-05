package com.essalud.samec.catalog.domain.model;

/** Dimension of a criterion; its factor weights the criterion in the macro process score. */
public enum CriterionType {
    STRUCTURE(1),
    PROCESS(2),
    RESULT(3);

    private final int scoreFactor;

    CriterionType(int scoreFactor) {
        this.scoreFactor = scoreFactor;
    }

    /** E x1, P x2, R x3 (accreditation scoring model). */
    public int scoreFactor() {
        return scoreFactor;
    }
}
