package com.essalud.samec.organizacion;

import com.essalud.samec.shared.trazabilidad.EntidadAuditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Institución Prestadora de Servicios de Salud (establecimiento) de una Red. */
@Getter
@Setter
@Entity
@Table(name = "ipress")
public class Ipress extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ipress")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_red", nullable = false)
    private Red red;

    @Column(name = "codigo", length = 50, nullable = false, unique = true)
    private String codigo;

    @Column(name = "nombre", length = 250, nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_complejidad", length = 5)
    private NivelComplejidad nivelComplejidad;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
