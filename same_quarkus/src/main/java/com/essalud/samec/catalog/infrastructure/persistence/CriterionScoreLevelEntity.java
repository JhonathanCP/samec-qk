package com.essalud.samec.catalog.infrastructure.persistence;

import org.hibernate.Length;

import com.essalud.samec.shared.trazabilidad.EntidadAuditable;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Meaning of score 0, 1 and 2 for one criterion. */
@Getter
@Setter
@Entity
@Table(name = "criterion_score_levels",
        uniqueConstraints = @UniqueConstraint(name = "uk_criterion_score_levels_criterion_score",
                columnNames = {"criterion_id", "score"}),
        check = @CheckConstraint(name = "ck_criterion_score_levels_score", constraint = "score between 0 and 2"))
public class CriterionScoreLevelEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_criterion_score_levels_criterion"))
    private CriterionEntity criterion;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "description", length = Length.LONG32, nullable = false)
    private String description;
}
