package com.essalud.samec.catalog.domain.model;

import java.util.List;

import com.essalud.samec.shared.domain.DomainException;

/**
 * One verifiable part of a criterion ("condición específica"). The weights of a criterion's
 * conditions add up to the criterion's maximum score. Document-review conditions have audit
 * sources (what the facility uploads); the others are checked on site and may say what to check
 * in {@code verificationGuide}.
 */
public record CriterionCondition(Long id, String description, int scoreWeight, EvaluationTechnique technique,
                                 Long institutionalProcessId, String facilityFunction, String responsibleRole,
                                 SourceLogic sourceLogic, List<NormativeReference> normativeReferences,
                                 List<AuditSource> sources, int displayOrder, String verificationGuide) {

    /** A condition without verification guide. */
    public CriterionCondition(Long id, String description, int scoreWeight, EvaluationTechnique technique,
                              Long institutionalProcessId, String facilityFunction, String responsibleRole,
                              SourceLogic sourceLogic, List<NormativeReference> normativeReferences,
                              List<AuditSource> sources, int displayOrder) {
        this(id, description, scoreWeight, technique, institutionalProcessId, facilityFunction, responsibleRole,
                sourceLogic, normativeReferences, sources, displayOrder, null);
    }

    public CriterionCondition {
        if (description == null || description.isBlank()) throw new DomainException("La descripción de la condición es obligatoria");
        if (scoreWeight < 0 || scoreWeight > Criterion.MAX_SCORE) throw new DomainException("Peso de condición inválido: " + scoreWeight);
        if (technique == null) throw new DomainException("La técnica de evaluación es obligatoria");
        normativeReferences = normativeReferences == null ? List.of() : List.copyOf(normativeReferences);
        sources = sources == null ? List.of() : List.copyOf(sources);
        verificationGuide = verificationGuide == null || verificationGuide.isBlank() ? null : verificationGuide.strip();
        if (!technique.isEvidenceBased() && !sources.isEmpty()) {
            throw new DomainException("Solo las condiciones de revisión de documentos tienen fuentes auditables");
        }
        if (sources.size() > 1 && sourceLogic == null) {
            throw new DomainException("Con varias fuentes indique si se exigen todas (Y) o alguna (O)");
        }
    }

    /** Sources the facility must upload for this condition. */
    public List<AuditSource> sourcesRequiringEvidence() {
        return sources.stream().filter(AuditSource::requiresFacilityEvidence).toList();
    }
}
