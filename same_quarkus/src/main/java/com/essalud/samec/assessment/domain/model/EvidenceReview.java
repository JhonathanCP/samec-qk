package com.essalud.samec.assessment.domain.model;

import java.time.LocalDateTime;

import com.essalud.samec.shared.domain.DomainException;

/** One decision of the evaluator on a submission. Kept as history: the last one is the current. */
public record EvidenceReview(Long id, ReviewVerdict verdict, Long observationTypeId, String comment,
                             Long reviewedBy, LocalDateTime reviewedAt) {

    public EvidenceReview {
        if (verdict == null) throw new DomainException("El resultado de la revisión es obligatorio");
        if (verdict == ReviewVerdict.OBSERVED && observationTypeId == null) {
            throw new DomainException("Indique el tipo de observación");
        }
        if (reviewedBy == null || reviewedAt == null) throw new DomainException("El revisor y la fecha son obligatorios");
    }
}
