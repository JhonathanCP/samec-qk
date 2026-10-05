package com.essalud.samec.catalog.infrastructure.persistence;

import com.essalud.samec.catalog.domain.model.SourceAvailability;
import com.essalud.samec.shared.trazabilidad.EntidadAuditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Auditable source of a condition: the evidence point the facility uploads files for. */
@Getter
@Setter
@Entity
@Table(name = "audit_sources",
        uniqueConstraints = @UniqueConstraint(name = "uk_audit_sources_condition_order",
                columnNames = {"condition_id", "display_order"}))
public class AuditSourceEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condition_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_audit_sources_condition"))
    private CriterionConditionEntity condition;

    @Column(name = "name", length = 1000, nullable = false)
    private String name;

    /** Where a predefined source can be consulted. */
    @Column(name = "reference_url", length = 1000)
    private String referenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", length = 30, nullable = false)
    private SourceAvailability availability;

    @Column(name = "applies_system_wide", nullable = false)
    private Boolean appliesSystemWide = false;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
