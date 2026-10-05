package com.essalud.samec.security;

/** Alcance de datos de un usuario según sus roles (ver RoleSets.alcanceDe). */
public enum Alcance {
    /** Ve todo el país (Admin, Sede Central). */
    NACIONAL,
    /** Ve las IPRESS de su Red. */
    RED,
    /** Ve solo su IPRESS. */
    IPRESS
}
