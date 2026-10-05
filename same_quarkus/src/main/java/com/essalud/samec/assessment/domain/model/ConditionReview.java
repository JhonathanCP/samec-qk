package com.essalud.samec.assessment.domain.model;

import java.time.LocalDateTime;
import java.util.List;

import com.essalud.samec.shared.domain.DomainException;

/**
 * On-site verification of a condition whose technique is not document review (observation,
 * interview, medical record audit): met or not, with a comment and optional photos.
 */
public record ConditionReview(Long id, Long selfAssessmentId, Long conditionId, boolean met, String comment,
                              List<Long> attachmentFileIds, Long reviewedBy, LocalDateTime reviewedAt) {

    public ConditionReview {
        if (selfAssessmentId == null) throw new DomainException("La autoevaluación es obligatoria");
        if (conditionId == null) throw new DomainException("La condición es obligatoria");
        if (reviewedBy == null || reviewedAt == null) throw new DomainException("El revisor y la fecha son obligatorios");
        if (!met && (comment == null || comment.isBlank())) {
            throw new DomainException("Indique por qué no se cumple la condición");
        }
        attachmentFileIds = attachmentFileIds == null ? List.of() : List.copyOf(attachmentFileIds);
    }
}
