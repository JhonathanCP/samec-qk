package com.essalud.samec.security;

import java.util.Set;

import org.eclipse.microprofile.jwt.JsonWebToken;

import com.essalud.samec.usuarios.Usuario;
import com.essalud.samec.usuarios.UsuarioRepository;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Completa la identidad de cada petición con un access token válido, a partir de la BD (no del
 * token): roles vigentes, permisos, alcance y Red/IPRESS. Así:
 * <ul>
 *   <li>una cuenta desactivada deja de funcionar de inmediato (401), aunque su token no haya vencido;</li>
 *   <li>un cambio de roles se aplica en la siguiente petición, sin volver a iniciar sesión;</li>
 *   <li>{@code @PermissionsAllowed("...")} se resuelve con {@link Permisos}.</li>
 * </ul>
 * Cuesta una consulta por petición (lo mismo que hacía Auditorías con loadUserByUsername).
 */
@ApplicationScoped
public class SesionAugmentor implements SecurityIdentityAugmentor {

    @Inject
    CargadorSesion cargador;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous() || !(identity.getPrincipal() instanceof JsonWebToken jwt)) {
            return Uni.createFrom().item(identity);
        }
        return context.runBlocking(() -> completar(identity, jwt));
    }

    private SecurityIdentity completar(SecurityIdentity identity, JsonWebToken jwt) {
        Long idUsuario;
        try {
            idUsuario = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new AuthenticationFailedException("Token sin usuario válido");
        }
        Sesion sesion = cargador.cargar(idUsuario);
        if (sesion == null) {
            throw new AuthenticationFailedException("Cuenta inexistente o desactivada");
        }

        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder()
                .setPrincipal(identity.getPrincipal())
                .addCredentials(identity.getCredentials())
                .addAttributes(identity.getAttributes())
                .addRoles(sesion.roles())
                .addAttribute(UsuarioActual.ATTR_ID, sesion.id())
                .addAttribute(UsuarioActual.ATTR_NOMBRE, sesion.nombreCompleto())
                .addAttribute(UsuarioActual.ATTR_ALCANCE, sesion.alcance())
                .addAttribute(UsuarioActual.ATTR_PERMISOS, sesion.permisos())
                .addPermissionChecker(permiso -> Uni.createFrom().item(sesion.permisos().contains(permiso.getName())));
        if (sesion.idRed() != null) builder.addAttribute(UsuarioActual.ATTR_ID_RED, sesion.idRed());
        if (sesion.idIpress() != null) builder.addAttribute(UsuarioActual.ATTR_ID_IPRESS, sesion.idIpress());
        return builder.build();
    }

    /** Lo que se necesita de la cuenta en cada petición. */
    public record Sesion(Long id, String nombreCompleto, Set<String> roles, Set<String> permisos,
                         Alcance alcance, Long idRed, Long idIpress) {
    }

    @ApplicationScoped
    public static class CargadorSesion {

        @Inject
        UsuarioRepository usuarios;

        /** null si la cuenta no existe o no puede usar el sistema. */
        @Transactional
        public Sesion cargar(Long idUsuario) {
            Usuario u = usuarios.findById(idUsuario);
            if (u == null || !Boolean.TRUE.equals(u.getActivo())) return null;
            Set<String> roles = u.codigosRol();
            return new Sesion(u.getId(), u.nombreCompleto(), roles, Permisos.de(roles), RoleSets.alcanceDe(roles),
                    u.redEfectiva() != null ? u.redEfectiva().getId() : null,
                    u.getIpress() != null ? u.getIpress().getId() : null);
        }
    }
}
