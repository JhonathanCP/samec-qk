package com.essalud.samec.catalog.domain.model;

import com.essalud.samec.shared.domain.DomainException;

/** A normative document cited by a condition, with the excerpt that applies in EsSalud. */
public record NormativeReference(Long documentId, String applicableExcerpt) {

    public NormativeReference {
        if (documentId == null) throw new DomainException("El documento normativo es obligatorio");
    }
}
