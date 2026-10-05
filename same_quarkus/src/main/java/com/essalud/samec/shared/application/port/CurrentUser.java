package com.essalud.samec.shared.application.port;

import com.essalud.samec.shared.error.AccesoDenegadoException;

/**
 * The user of the current request, as the use cases need it. Implemented from the validated
 * token and the session (security module), so the application layer does not know about JWT.
 */
public interface CurrentUser {

    Long id();

    boolean hasPermission(String permission);

    /** The user's own facility (IPRESS) when their scope is a facility; null otherwise. */
    Long facilityId();

    /** Whether the user sees every facility of the country. */
    boolean hasNationalScope();

    /** Whether the user's data scope (national, network or own facility) covers this facility. */
    boolean canAccessFacility(Long facilityId);

    /** Fails with 403 when the scope does not cover the facility. */
    default void ensureCanAccessFacility(Long facilityId) {
        if (!canAccessFacility(facilityId)) {
            throw new AccesoDenegadoException("No tiene acceso a la información de esta IPRESS.");
        }
    }
}
