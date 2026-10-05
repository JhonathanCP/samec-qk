package com.essalud.samec.assessment.domain.model;

import java.time.LocalDateTime;

import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.shared.domain.DomainException;

/** The score (0, 1 or 2) a criterion got in a self-assessment. */
public record CriterionScore(Long id, Long selfAssessmentId, Long criterionId, int score, ScoreOrigin origin,
                             String justification, Long scoredBy, LocalDateTime scoredAt) {

    public CriterionScore {
        if (selfAssessmentId == null) throw new DomainException("La autoevaluación es obligatoria");
        if (criterionId == null) throw new DomainException("El criterio es obligatorio");
        if (score < 0 || score > Criterion.MAX_SCORE) throw new DomainException("Puntaje fuera de rango (0 a 2): " + score);
        if (origin == null) throw new DomainException("El origen del puntaje es obligatorio");
        if (origin == ScoreOrigin.MANUAL && scoredBy == null) throw new DomainException("Una calificación manual debe indicar el evaluador");
        if (scoredAt == null) throw new DomainException("La fecha de calificación es obligatoria");
    }

    public static CriterionScore preScored(Long selfAssessmentId, Long criterionId, LocalDateTime when) {
        return new CriterionScore(null, selfAssessmentId, criterionId, Criterion.MAX_SCORE, ScoreOrigin.PRE_SCORED,
                "Todas sus fuentes son predefinidas", null, when);
    }
}
