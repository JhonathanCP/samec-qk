package com.essalud.samec.catalog.infrastructure.persistence;

import org.hibernate.Length;

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

@Getter
@Setter
@Entity
@Table(name = "standards",
        uniqueConstraints = @UniqueConstraint(name = "uk_standards_code", columnNames = "code"),
        indexes = @Index(name = "ix_standards_macro_process_id", columnList = "macro_process_id"))
public class StandardEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "macro_process_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_standards_macro_process"))
    private MacroProcessEntity macroProcess;

    @Column(name = "code", length = 20, nullable = false)
    private String code;

    @Column(name = "statement", length = Length.LONG32, nullable = false)
    private String statement;
}
