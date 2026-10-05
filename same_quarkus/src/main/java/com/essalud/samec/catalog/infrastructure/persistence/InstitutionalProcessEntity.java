package com.essalud.samec.catalog.infrastructure.persistence;

import com.essalud.samec.shared.trazabilidad.EntidadAuditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Institutional process; a sub-process has a parent. */
@Getter
@Setter
@Entity
@Table(name = "institutional_processes",
        uniqueConstraints = @UniqueConstraint(name = "uk_institutional_processes_parent_name",
                columnNames = {"parent_id", "name"}),
        indexes = @Index(name = "ix_institutional_processes_parent_id", columnList = "parent_id"))
public class InstitutionalProcessEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "fk_institutional_processes_parent"))
    private InstitutionalProcessEntity parent;

    @Column(name = "name", length = 250, nullable = false)
    private String name;
}
