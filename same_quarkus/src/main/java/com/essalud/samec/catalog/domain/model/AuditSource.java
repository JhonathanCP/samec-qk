package com.essalud.samec.catalog.domain.model;

import com.essalud.samec.shared.domain.DomainException;

/** A document or record ("fuente auditable") that supports a condition. */
public record AuditSource(Long id, String name, String referenceUrl, SourceAvailability availability,
                          boolean appliesSystemWide, int displayOrder) {

    public AuditSource {
        if (name == null || name.isBlank()) throw new DomainException("El nombre de la fuente es obligatorio");
        if (availability == null) throw new DomainException("La disponibilidad de la fuente es obligatoria");
        if (availability == SourceAvailability.PREDEFINED && (referenceUrl == null || referenceUrl.isBlank())) {
            throw new DomainException("Una fuente predefinida debe tener su enlace de referencia");
        }
    }

    /** True when the facility must upload evidence for this source. */
    public boolean requiresFacilityEvidence() {
        return availability == SourceAvailability.PROVIDED_BY_FACILITY;
    }
}
