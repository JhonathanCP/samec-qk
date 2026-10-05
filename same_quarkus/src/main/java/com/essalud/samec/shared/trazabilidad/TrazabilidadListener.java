package com.essalud.samec.shared.trazabilidad;

import java.time.LocalDateTime;

import com.essalud.samec.security.UsuarioActual;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * Llena la trazabilidad de {@link EntidadAuditable}. Con sesión, el usuario es siempre el de la
 * sesión aunque el código haya puesto otro; sin sesión (arranque, tareas programadas, login,
 * autorregistro) solo se ponen las fechas y se respeta el usuario que haya asignado el código.
 */
public class TrazabilidadListener {

    @PrePersist
    void alCrear(EntidadAuditable entidad) {
        LocalDateTime ahora = LocalDateTime.now();
        if (entidad.getCreatedAt() == null) entidad.setCreatedAt(ahora);
        entidad.setUpdatedAt(ahora);

        Long idUsuario = UsuarioActual.idEnContexto();
        if (idUsuario != null) {
            entidad.setCreatedBy(idUsuario);
            entidad.setUpdatedBy(idUsuario);
        }
    }

    @PreUpdate
    void alModificar(EntidadAuditable entidad) {
        entidad.setUpdatedAt(LocalDateTime.now());
        Long idUsuario = UsuarioActual.idEnContexto();
        if (idUsuario != null) entidad.setUpdatedBy(idUsuario);
    }
}
