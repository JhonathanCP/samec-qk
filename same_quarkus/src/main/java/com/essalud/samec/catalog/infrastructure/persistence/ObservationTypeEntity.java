package com.essalud.samec.catalog.infrastructure.persistence;

import com.essalud.samec.shared.trazabilidad.EntidadAuditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "observation_types",
        uniqueConstraints = @UniqueConstraint(name = "uk_observation_types_code", columnNames = "code"))
public class ObservationTypeEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 40, nullable = false)
    private String code;

    @Column(name = "name", length = 250, nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;
}
