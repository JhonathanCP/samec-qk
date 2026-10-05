package com.essalud.samec.catalog.domain.model;

import com.essalud.samec.shared.domain.DomainException;

/** EsSalud institutional process that groups documents; a sub-process points to its parent. */
public record InstitutionalProcess(Long id, Long parentId, String name) {

    public InstitutionalProcess {
        if (name == null || name.isBlank()) throw new DomainException("El nombre del proceso es obligatorio");
    }

    public boolean isSubProcess() {
        return parentId != null;
    }
}
