package com.essalud.samec.catalog.domain.model;

import com.essalud.samec.shared.domain.DomainException;

public record Standard(Long id, Long macroProcessId, String code, String statement) {

    public Standard {
        if (macroProcessId == null) throw new DomainException("El estándar debe pertenecer a un macroproceso");
        if (code == null || code.isBlank()) throw new DomainException("El código del estándar es obligatorio");
        if (statement == null || statement.isBlank()) throw new DomainException("El enunciado del estándar es obligatorio");
    }
}
