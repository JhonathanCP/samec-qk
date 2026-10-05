package com.essalud.samec.security;

import java.util.Collection;
import java.util.Set;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/**
 * Usuario de la sesión, con los datos que carga {@link SesionAugmentor} desde la BD en cada
 * petición: id, nombre, roles vigentes, permisos, alcance y su Red/IPRESS. Inyectarlo en los
 * servicios en vez de leer el token.
 */
@RequestScoped
public class UsuarioActual {

    static final String ATTR_ID = "samec.idUsuario";
    static final String ATTR_NOMBRE = "samec.nombreCompleto";
    static final String ATTR_ID_RED = "samec.idRed";
    static final String ATTR_ID_IPRESS = "samec.idIpress";
    static final String ATTR_ALCANCE = "samec.alcance";
    static final String ATTR_PERMISOS = "samec.permisos";

    @Inject
    SecurityIdentity identity;

    public boolean autenticado() {
        return !identity.isAnonymous();
    }

    public Long id() {
        return identity.getAttribute(ATTR_ID);
    }

    public String username() {
        return identity.isAnonymous() ? null : identity.getPrincipal().getName();
    }

    public String nombreCompleto() {
        return identity.getAttribute(ATTR_NOMBRE);
    }

    public Set<String> roles() {
        return identity.getRoles();
    }

    public Long idRed() {
        return identity.getAttribute(ATTR_ID_RED);
    }

    public Long idIpress() {
        return identity.getAttribute(ATTR_ID_IPRESS);
    }

    public Alcance alcance() {
        Alcance alcance = identity.getAttribute(ATTR_ALCANCE);
        return alcance != null ? alcance : Alcance.NACIONAL;
    }

    public Set<String> permisos() {
        Set<String> permisos = identity.getAttribute(ATTR_PERMISOS);
        return permisos != null ? permisos : Set.of();
    }

    public boolean tieneRol(String rol) {
        return identity.hasRole(rol);
    }

    public boolean tieneAlgunRol(Collection<String> roles) {
        return roles.stream().anyMatch(identity::hasRole);
    }

    public boolean tienePermiso(String permiso) {
        return permisos().contains(permiso);
    }

    /** ¿El alcance del usuario cubre esta IPRESS (de esta Red)? */
    public boolean cubreIpress(Long idIpress, Long idRedDeLaIpress) {
        return switch (alcance()) {
            case NACIONAL -> true;
            case RED -> idRed() != null && idRed().equals(idRedDeLaIpress);
            case IPRESS -> idIpress() != null && idIpress().equals(idIpress);
        };
    }

    // ── Acceso estático, para código que no es un bean (listeners JPA, bitácora) ────────────

    /** id del usuario con sesión en el hilo actual; null sin sesión (arranque, tareas, login). */
    public static Long idEnContexto() {
        SecurityIdentity identidad = identidadEnContexto();
        return identidad != null ? identidad.getAttribute(ATTR_ID) : null;
    }

    public static String nombreEnContexto() {
        SecurityIdentity identidad = identidadEnContexto();
        return identidad != null ? identidad.getAttribute(ATTR_NOMBRE) : null;
    }

    private static SecurityIdentity identidadEnContexto() {
        ArcContainer arc = Arc.container();
        if (arc == null || !arc.requestContext().isActive()) return null;
        InstanceHandle<CurrentIdentityAssociation> asociacion = arc.instance(CurrentIdentityAssociation.class);
        if (!asociacion.isAvailable()) return null;
        SecurityIdentity identidad = asociacion.get().getIdentity();
        return identidad == null || identidad.isAnonymous() ? null : identidad;
    }
}
