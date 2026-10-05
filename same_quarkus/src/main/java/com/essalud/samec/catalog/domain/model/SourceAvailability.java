package com.essalud.samec.catalog.domain.model;

/** Who provides an auditable source. */
public enum SourceAvailability {
    /** The facility must upload it. */
    PROVIDED_BY_FACILITY,
    /** An institutional document that already exists (has a reference URL): nothing to upload. */
    PREDEFINED,
    /** The institution has not produced it yet ("No implementado"): it cannot be satisfied. */
    NOT_IMPLEMENTED
}
