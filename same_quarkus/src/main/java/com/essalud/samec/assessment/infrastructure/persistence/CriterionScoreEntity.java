package com.essalud.samec.assessment.infrastructure.persistence;

import java.time.LocalDateTime;

import com.essalud.samec.assessment.domain.model.ScoreOrigin;
import com.essalud.samec.catalog.infrastructure.persistence.CriterionEntity;
import com.essalud.samec.shared.trazabilidad.EntidadAuditable;
import com.essalud.samec.usuarios.Usuario;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "criterion_scores",
        uniqueConstraints = @UniqueConstraint(name = "uk_criterion_scores_assessment_criterion",
                columnNames = {"self_assessment_id", "criterion_id"}),
        indexes = @Index(name = "ix_criterion_scores_criterion_id", columnList = "criterion_id"),
        check = {
                @CheckConstraint(name = "ck_criterion_scores_score", constraint = "score between 0 and 2"),
                @CheckConstraint(name = "ck_criterion_scores_manual_scorer",
                        constraint = "origin <> 'MANUAL' or scored_by_user_id is not null")
        })
public class CriterionScoreEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "self_assessment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_criterion_scores_self_assessment"))
    private SelfAssessmentEntity selfAssessment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_criterion_scores_criterion"))
    private CriterionEntity criterion;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", length = 15, nullable = false)
    private ScoreOrigin origin;

    @Column(name = "justification", length = 2000)
    private String justification;

    /** Null when the system pre-scored it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scored_by_user_id", foreignKey = @ForeignKey(name = "fk_criterion_scores_scored_by_user"))
    private Usuario scoredBy;

    @Column(name = "scored_at", nullable = false)
    private LocalDateTime scoredAt;
}
