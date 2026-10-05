package com.essalud.samec.shared.bitacora;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Una acción sobre una entidad de negocio: quién hizo qué y cuándo. Los campos updated_* de cada
 * tabla solo guardan la última modificación; esto conserva todas. El nombre del usuario se guarda
 * tal como era en ese momento.
 */
@Getter
@Setter
@Entity
@Table(name = "bitacora_cambios", indexes = @Index(name = "ix_bitacora_entidad", columnList = "entidad, id_entidad"))
public class BitacoraCambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bitacora")
    private Long id;

    /** Tipo de entidad, p. ej. "USUARIO" o, en el core, "AUTOEVALUACION". */
    @Column(name = "entidad", length = 40, nullable = false)
    private String entidad;

    @Column(name = "id_entidad", nullable = false)
    private Long idEntidad;

    /** Código de la acción (CREAR, MODIFICAR, APROBAR, …). */
    @Column(name = "accion", length = 40, nullable = false)
    private String accion;

    @Column(name = "detalle", length = 4000)
    private String detalle;

    /** null si la acción no la hizo un usuario con sesión. */
    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "usuario_nombre", length = 250)
    private String usuarioNombre;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
