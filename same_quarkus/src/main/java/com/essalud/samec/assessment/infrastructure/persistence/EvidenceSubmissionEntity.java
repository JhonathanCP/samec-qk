package com.essalud.samec.assessment.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.essalud.samec.assessment.domain.model.SubmissionStatus;
import com.essalud.samec.catalog.infrastructure.persistence.AuditSourceEntity;
import com.essalud.samec.shared.trazabilidad.EntidadAuditable;
import com.essalud.samec.usuarios.Usuario;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Evidence of one audit source in one self-assessment: 1..N files (evidence_files) and/or a link.
 * The domain guarantees it has at least one of them.
 */
@Getter
@Setter
@Entity
@Table(name = "evidence_submissions",
        uniqueConstraints = @UniqueConstraint(name = "uk_evidence_submissions_assessment_source",
                columnNames = {"self_assessment_id", "audit_source_id"}),
        indexes = {
                @Index(name = "ix_evidence_submissions_audit_source_id", columnList = "audit_source_id"),
                @Index(name = "ix_evidence_submissions_assessment_status", columnList = "self_assessment_id, status")
        })
public class EvidenceSubmissionEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "self_assessment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_evidence_submissions_self_assessment"))
    private SelfAssessmentEntity selfAssessment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_source_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_evidence_submissions_audit_source"))
    private AuditSourceEntity auditSource;

    @Column(name = "external_url", length = 1000)
    private String externalUrl;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private SubmissionStatus status = SubmissionStatus.PENDING_REVIEW;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_evidence_submissions_submitted_by_user"))
    private Usuario submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<EvidenceFileEntity> files = new ArrayList<>();

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("reviewedAt")
    private List<EvidenceReviewEntity> reviews = new ArrayList<>();

    public void addFile(EvidenceFileEntity file) {
        file.setSubmission(this);
        files.add(file);
    }

    public void addReview(EvidenceReviewEntity review) {
        review.setSubmission(this);
        reviews.add(review);
    }
}
