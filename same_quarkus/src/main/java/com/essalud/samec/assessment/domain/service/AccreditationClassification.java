package com.essalud.samec.assessment.domain.service;

import java.math.BigDecimal;

/** Outcome of the accreditation according to the final score (%). */
public enum AccreditationClassification {
    NOT_APPROVED_RESTART("No aprobado: reiniciar desde la autoevaluación (< 50%)"),
    REMEDIATE_IN_9_MONTHS("No aprobado: subsanar en 9 meses (50% a < 70%)"),
    REMEDIATE_IN_6_MONTHS("No aprobado: subsanar en 6 meses (70% a < 85%)"),
    APPROVED("Aprobado (85% o más)");

    private final String label;

    AccreditationClassification(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static AccreditationClassification of(BigDecimal finalScore) {
        if (finalScore.compareTo(BigDecimal.valueOf(50)) < 0) return NOT_APPROVED_RESTART;
        if (finalScore.compareTo(BigDecimal.valueOf(70)) < 0) return REMEDIATE_IN_9_MONTHS;
        if (finalScore.compareTo(BigDecimal.valueOf(85)) < 0) return REMEDIATE_IN_6_MONTHS;
        return APPROVED;
    }
}
