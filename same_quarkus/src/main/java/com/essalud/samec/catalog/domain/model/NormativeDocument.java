package com.essalud.samec.catalog.domain.model;

import com.essalud.samec.shared.domain.DomainException;

public record NormativeDocument(Long id, String title, String url) {

    public NormativeDocument {
        if (title == null || title.isBlank()) throw new DomainException("El título del documento normativo es obligatorio");
    }
}
