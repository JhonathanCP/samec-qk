package com.essalud.samec.catalog.domain.model;

import java.math.BigDecimal;

import com.essalud.samec.shared.domain.DomainException;

/**
 * Macro process of the LEA. {@code scoreWeight} is its share (in %) of the final accreditation
 * score; the weights of all macro processes add up to 100 (e.g. DIR 8, GRH 6 ... GER = 35).
 */
public record MacroProcess(Long id, String code, String name, MacroProcessCategory category, BigDecimal scoreWeight,
                           int displayOrder) {

    public MacroProcess {
        if (code == null || code.isBlank()) throw new DomainException("El código del macroproceso es obligatorio");
        if (name == null || name.isBlank()) throw new DomainException("El nombre del macroproceso es obligatorio");
        if (category == null) throw new DomainException("La categoría del macroproceso es obligatoria");
        if (scoreWeight == null || scoreWeight.signum() < 0 || scoreWeight.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new DomainException("El peso del macroproceso debe estar entre 0 y 100");
        }
    }
}
