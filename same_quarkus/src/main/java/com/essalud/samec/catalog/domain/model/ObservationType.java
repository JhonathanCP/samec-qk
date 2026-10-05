package com.essalud.samec.catalog.domain.model;

import com.essalud.samec.shared.domain.DomainException;

/** Reason an evaluator gives when observing evidence (e.g. wrong year, wrong document). */
public record ObservationType(Long id, String code, String name, boolean active) {

    public ObservationType {
        if (code == null || code.isBlank()) throw new DomainException("El código del tipo de observación es obligatorio");
        if (name == null || name.isBlank()) throw new DomainException("El nombre del tipo de observación es obligatorio");
    }
}
