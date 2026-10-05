package com.essalud.samec.shared.trazabilidad;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Base de toda entidad de negocio: guarda cuándo y quién creó la fila, y cuándo y quién la
 * modificó por última vez. Los llena {@link TrazabilidadListener} al guardar, así que ni los
 * servicios ni los recursos los ponen a mano. El historial completo de acciones (no solo la
 * última) va en la bitácora (ver BitacoraService).
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(TrazabilidadListener.class)
public abstract class EntidadAuditable {

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** id_usuario de quien creó la fila; null si la creó el sistema (arranque, tareas, autorregistro). */
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
