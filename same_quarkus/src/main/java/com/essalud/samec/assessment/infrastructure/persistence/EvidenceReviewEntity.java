package com.essalud.samec.assessment.infrastructure.persistence;

import java.time.LocalDateTime;

import com.essalud.samec.assessment.domain.model.ReviewVerdict;
import com.essalud.samec.catalog.infrastructure.persistence.ObservationTypeEntity;
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
import lombok.Getter;
import lombok.Setter;

/** Review history of a submission (approve / observe, then re-submit, and so on). */
@Getter
@Setter
@Entity
@Table(name = "evidence_reviews",
        indexes = {
                @Index(name = "ix_evidence_reviews_submission_id", columnList = "evidence_submission_id"),
                @Index(name = "ix_evidence_reviews_observation_type_id", columnList = "observation_type_id")
        },
        check = @CheckConstraint(name = "ck_evidence_reviews_observation_type",
                constraint = "verdict <> 'OBSERVED' or observation_type_id is not null"))
public class EvidenceReviewEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_submission_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_evidence_reviews_submission"))
    private EvidenceSubmissionEntity submission;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", length = 10, nullable = false)
    private ReviewVerdict verdict;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "observation_type_id",
            foreignKey = @ForeignKey(name = "fk_evidence_reviews_observation_type"))
    private ObservationTypeEntity observationType;

    @Column(name = "comment_text", length = 2000)
    private String commentText;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewed_by_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_evidence_reviews_reviewed_by_user"))
    private Usuario reviewedBy;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;
}
