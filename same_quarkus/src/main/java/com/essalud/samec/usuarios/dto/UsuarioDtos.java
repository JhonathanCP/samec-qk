package com.essalud.samec.usuarios.dto;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.essalud.samec.organizacion.Red;
import com.essalud.samec.security.Alcance;
import com.essalud.samec.security.Permisos;
import com.essalud.samec.security.RoleSets;
import com.essalud.samec.usuarios.EstadoRegistro;
import com.essalud.samec.usuarios.Rol;
import com.essalud.samec.usuarios.Usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTOs del módulo de usuarios y de la sesión. */
public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    /** @param alcance de qué depende el rol: NACIONAL (nada), RED (pide Red) o IPRESS (pide IPRESS). */
    public record RolDto(Long id, String codigo, String nombre, String descripcion, Alcance alcance) {
        public static RolDto de(Rol r) {
            return new RolDto(r.getId(), r.getCodigo(), r.getNombre(), r.getDescripcion(),
                    RoleSets.alcanceDe(List.of(r.getCodigo())));
        }
    }

    /** Usuario de la sesión: lo devuelven login, refresh y /auth/me. */
    public record UsuarioSesionDto(Long id, String username, String nombre, String apellido, String email,
                                   List<String> roles, List<String> permisos, Alcance alcance,
                                   Long idRed, String redNombre, Long idIpress, String ipressNombre) {

        public static UsuarioSesionDto de(Usuario u) {
            List<String> roles = u.codigosRol().stream().sorted().toList();
            Red red = u.redEfectiva();
            return new UsuarioSesionDto(u.getId(), u.getUsername(), u.getNombre(), u.getApellido(), u.getEmail(),
                    roles, Permisos.de(roles).stream().sorted().toList(), RoleSets.alcanceDe(roles),
                    red != null ? red.getId() : null, red != null ? red.getNombre() : null,
                    u.getIpress() != null ? u.getIpress().getId() : null,
                    u.getIpress() != null ? u.getIpress().getNombre() : null);
        }
    }

    /** Fila del listado / detalle de usuarios. */
    public record UsuarioDto(Long id, String username, String nombre, String apellido, String email, boolean activo,
                             List<RolDto> roles, Alcance alcance,
                             Long idRed, String redNombre, Long idIpress, String ipressNombre,
                             boolean autoregistrado, EstadoRegistro registroEstado, String registroComentario,
                             String registroRevisadoPorNombre, LocalDateTime registroRevisadoAt,
                             LocalDateTime registroSolicitadoAt, LocalDateTime ultimoAcceso,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {

        public static UsuarioDto de(Usuario u) {
            Red red = u.redEfectiva();
            return new UsuarioDto(u.getId(), u.getUsername(), u.getNombre(), u.getApellido(), u.getEmail(),
                    Boolean.TRUE.equals(u.getActivo()),
                    u.getRoles().stream().sorted(Comparator.comparing(Rol::getCodigo)).map(RolDto::de).toList(),
                    RoleSets.alcanceDe(u.codigosRol()),
                    red != null ? red.getId() : null, red != null ? red.getNombre() : null,
                    u.getIpress() != null ? u.getIpress().getId() : null,
                    u.getIpress() != null ? u.getIpress().getNombre() : null,
                    Boolean.TRUE.equals(u.getAutoregistrado()), u.getRegistroEstado(), u.getRegistroComentario(),
                    u.getRegistroRevisadoPorNombre(), u.getRegistroRevisadoAt(), u.getRegistroSolicitadoAt(),
                    u.getUltimoAcceso(), u.getCreatedAt(), u.getUpdatedAt());
        }
    }

    /**
     * Alta o edición por un administrador. La contraseña no se define aquí: el usuario fija la suya
     * con "¿Olvidó su contraseña?" (le llega el enlace por correo).
     */
    public record UsuarioRequest(
            @NotBlank(message = "Debe indicar el nombre de usuario.")
            @Size(max = 80, message = "El nombre de usuario admite hasta 80 caracteres.") String username,
            @NotBlank(message = "Debe indicar el nombre.") @Size(max = 120) String nombre,
            @NotBlank(message = "Debe indicar el apellido.") @Size(max = 120) String apellido,
            @NotBlank(message = "Debe indicar el correo.") @Email(message = "El correo no es válido.")
            @Size(max = 180) String email,
            @NotEmpty(message = "Debe asignar al menos un rol.") List<String> roles,
            Long idRed,
            Long idIpress) {
    }

    /** Solicitud pública desde /auth/registro: queda PENDIENTE hasta que Sede Central la revise. */
    public record AutorregistroRequest(
            @NotBlank(message = "Debe indicar el nombre.") @Size(max = 120) String nombre,
            @NotBlank(message = "Debe indicar el apellido.") @Size(max = 120) String apellido,
            @NotBlank(message = "Debe indicar su correo institucional.") @Size(max = 180) String email,
            @NotBlank(message = "Debe indicar una contraseña.") String password,
            @NotBlank(message = "Debe seleccionar un rol.") String rol,
            Long idRed,
            Long idIpress,
            String recaptchaToken) {
    }

    public record ResolverSolicitudRequest(
            @NotNull(message = "Debe indicar el estado (APROBADO o RECHAZADO).") EstadoRegistro estado,
            @Size(max = 500, message = "El comentario admite hasta 500 caracteres.") String comentario) {
    }

    public record EstadoRequest(@NotNull(message = "Debe indicar el nuevo estado (activo).") Boolean activo) {
    }

    public record CambiarPasswordRequest(
            @NotBlank(message = "Debe indicar su contraseña actual.") String passwordActual,
            @NotBlank(message = "Debe indicar la nueva contraseña.") String passwordNueva) {
    }

    public record MensajeResponse(String message) {
    }
}
