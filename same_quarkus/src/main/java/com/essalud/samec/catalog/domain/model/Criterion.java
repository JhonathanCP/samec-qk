package com.essalud.samec.catalog.domain.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.essalud.samec.shared.domain.DomainException;

/** An accreditation criterion of the LEA, scored from 0 to {@value #MAX_SCORE}. */
public record Criterion(Long id, Long standardId, String code, String statement, CriterionType type,
                        Set<FacilityCategory> applicableCategories, List<ScoreLevel> scoreLevels,
                        List<CriterionCondition> conditions) {

    public static final int MAX_SCORE = 2;

    public Criterion {
        if (standardId == null) throw new DomainException("El criterio debe pertenecer a un estándar");
        if (code == null || code.isBlank()) throw new DomainException("El código del criterio es obligatorio");
        if (statement == null || statement.isBlank()) throw new DomainException("El enunciado del criterio es obligatorio");
        if (type == null) throw new DomainException("El tipo de criterio es obligatorio");
        applicableCategories = applicableCategories == null || applicableCategories.isEmpty()
                ? Set.of() : Set.copyOf(EnumSet.copyOf(applicableCategories));
        scoreLevels = scoreLevels == null ? List.of() : List.copyOf(scoreLevels);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        int total = conditions.stream().mapToInt(CriterionCondition::scoreWeight).sum();
        if (!conditions.isEmpty() && total != MAX_SCORE) {
            throw new DomainException("Los pesos de las condiciones de " + code + " suman " + total + "; deben sumar " + MAX_SCORE);
        }
    }

    public boolean appliesTo(FacilityCategory category) {
        return applicableCategories.contains(category);
    }

    /**
     * True when every condition is document review and all its sources are predefined: the
     * facility has nothing to upload and the criterion can be pre-scored with the maximum.
     */
    public boolean isFullyPredefined() {
        return !conditions.isEmpty() && conditions.stream().allMatch(c ->
                c.technique().isEvidenceBased() && !c.sources().isEmpty()
                        && c.sources().stream().allMatch(s -> s.availability() == SourceAvailability.PREDEFINED));
    }
}
