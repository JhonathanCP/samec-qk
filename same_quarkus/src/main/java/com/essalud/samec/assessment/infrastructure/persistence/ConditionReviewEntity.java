package com.essalud.samec.assessment.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.essalud.samec.catalog.infrastructure.persistence.CriterionConditionEntity;
import com.essalud.samec.shared.trazabilidad.EntidadAuditable;
import com.essalud.samec.usuarios.Usuario;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** On-site verification (observation, interview, medical record audit) of a condition. */
@Getter
@Setter
@Entity
@Table(name = "condition_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_condition_reviews_assessment_condition",
                columnNames = {"self_assessment_id", "criterion_condition_id"}),
        indexes = @Index(name = "ix_condition_reviews_condition_id", columnList = "criterion_condition_id"))
public class ConditionReviewEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "self_assessment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_condition_reviews_self_assessment"))
    private SelfAssessmentEntity selfAssessment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_condition_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_condition_reviews_condition"))
    private CriterionConditionEntity condition;

    @Column(name = "is_met", nullable = false)
    private Boolean met;

    @Column(name = "comment_text", length = 2000)
    private String commentText;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewed_by_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_condition_reviews_reviewed_by_user"))
    private Usuario reviewedBy;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    @OneToMany(mappedBy = "conditionReview", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ConditionReviewFileEntity> attachments = new ArrayList<>();

    public void addAttachment(ConditionReviewFileEntity attachment) {
        attachment.setConditionReview(this);
        attachments.add(attachment);
    }
}
