package com.essalud.samec.catalog.infrastructure.persistence;

import java.math.BigDecimal;

import com.essalud.samec.catalog.domain.model.MacroProcessCategory;
import com.essalud.samec.shared.trazabilidad.EntidadAuditable;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "macro_processes",
        uniqueConstraints = @UniqueConstraint(name = "uk_macro_processes_code", columnNames = "code"),
        check = @CheckConstraint(name = "ck_macro_processes_score_weight", constraint = "score_weight between 0 and 100"))
public class MacroProcessEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 20, nullable = false)
    private String code;

    @Column(name = "name", length = 250, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private MacroProcessCategory category;

    /** Share (%) of the final accreditation score. */
    @Column(name = "score_weight", precision = 5, scale = 2, nullable = false)
    private BigDecimal scoreWeight;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
