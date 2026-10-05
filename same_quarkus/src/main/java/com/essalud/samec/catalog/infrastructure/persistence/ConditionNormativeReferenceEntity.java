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

/** Link condition ↔ normative document, with the excerpt that applies in EsSalud. */
@Getter
@Setter
@Entity
@Table(name = "condition_normative_references",
        uniqueConstraints = @UniqueConstraint(name = "uk_condition_normative_references_condition_document",
                columnNames = {"condition_id", "normative_document_id"}),
        indexes = @Index(name = "ix_condition_normative_references_document_id", columnList = "normative_document_id"))
public class ConditionNormativeReferenceEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condition_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_condition_normative_references_condition"))
    private CriterionConditionEntity condition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "normative_document_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_condition_normative_references_document"))
    private NormativeDocumentEntity normativeDocument;

    @Column(name = "applicable_excerpt", length = Length.LONG32)
    private String applicableExcerpt;
}
