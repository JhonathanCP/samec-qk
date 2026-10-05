package com.essalud.samec.assessment.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.essalud.samec.assessment.domain.model.AssessmentStatus;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.organizacion.Ipress;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "self_assessments",
        uniqueConstraints = @UniqueConstraint(name = "uk_self_assessments_facility_year",
                columnNames = {"facility_id", "period_year"}),
        check = {
                @CheckConstraint(name = "ck_self_assessments_period_year", constraint = "period_year >= 2000"),
                @CheckConstraint(name = "ck_self_assessments_final_score",
                        constraint = "final_score is null or final_score between 0 and 100"),
                @CheckConstraint(name = "ck_self_assessments_closed",
                        constraint = "status <> 'CLOSED' or (final_score is not null and closed_at is not null)")
        })
public class SelfAssessmentEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_self_assessments_facility"))
    private Ipress facility;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    /** Category of the facility when the assessment started (snapshot). */
    @Enumerated(EnumType.STRING)
    @Column(name = "facility_category", length = 10, nullable = false)
    private FacilityCategory facilityCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private AssessmentStatus status = AssessmentStatus.OPEN;

    @Column(name = "final_score", precision = 5, scale = 2)
    private BigDecimal finalScore;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_user_id", foreignKey = @ForeignKey(name = "fk_self_assessments_closed_by_user"))
    private Usuario closedBy;

    /** Document upload closed by the evaluator since this moment (null = open). */
    @Column(name = "evidence_locked_at")
    private LocalDateTime evidenceLockedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_locked_by_user_id",
            foreignKey = @ForeignKey(name = "fk_self_assessments_evidence_locked_by_user"))
    private Usuario evidenceLockedBy;
}
