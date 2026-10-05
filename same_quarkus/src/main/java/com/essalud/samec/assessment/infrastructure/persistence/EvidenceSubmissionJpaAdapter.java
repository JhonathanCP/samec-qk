package com.essalud.samec.assessment.infrastructure.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.essalud.samec.assessment.domain.model.EvidenceFile;
import com.essalud.samec.assessment.domain.model.EvidenceReview;
import com.essalud.samec.assessment.domain.model.EvidenceSubmission;
import com.essalud.samec.assessment.domain.model.SubmissionStatus;
import com.essalud.samec.assessment.domain.port.out.EvidenceSubmissionRepository;
import com.essalud.samec.catalog.infrastructure.persistence.AuditSourceEntity;
import com.essalud.samec.catalog.infrastructure.persistence.ObservationTypeEntity;
import com.essalud.samec.filestorage.infrastructure.persistence.StoredFileEntity;
import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;
import com.essalud.samec.shared.infrastructure.persistence.ChildCollections;
import com.essalud.samec.shared.infrastructure.persistence.JpaCrudAdapter;
import com.essalud.samec.usuarios.Usuario;

import jakarta.enterprise.context.ApplicationScoped;

/** Evidence submission aggregate with its files and review history. */
@ApplicationScoped
public class EvidenceSubmissionJpaAdapter extends JpaCrudAdapter<EvidenceSubmission, EvidenceSubmissionEntity, Long>
        implements EvidenceSubmissionRepository {

    public EvidenceSubmissionJpaAdapter() {
        super(EvidenceSubmissionEntity.class, EvidenceSubmissionEntity::new, "Evidencia");
    }

    @Override
    protected Long idOf(EvidenceSubmission model) {
        return model.id();
    }

    @Override
    protected EvidenceSubmission toDomain(EvidenceSubmissionEntity e) {
        List<EvidenceFile> files = e.getFiles().stream()
                .map(f -> new EvidenceFile(f.getId(), f.getStoredFile().getId()))
                .toList();
        List<EvidenceReview> reviews = e.getReviews().stream()
                .sorted(Comparator.comparing(EvidenceReviewEntity::getReviewedAt)
                        .thenComparing(EvidenceReviewEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(r -> new EvidenceReview(r.getId(), r.getVerdict(),
                        r.getObservationType() == null ? null : r.getObservationType().getId(),
                        r.getCommentText(), r.getReviewedBy().getId(), r.getReviewedAt()))
                .toList();
        return new EvidenceSubmission(e.getId(), e.getSelfAssessment().getId(), e.getAuditSource().getId(),
                e.getExternalUrl(), e.getNotes(), e.getStatus(), e.getSubmittedBy().getId(), e.getSubmittedAt(),
                files, reviews);
    }

    @Override
    protected void copyToEntity(EvidenceSubmission m, EvidenceSubmissionEntity e) {
        e.setSelfAssessment(reference(SelfAssessmentEntity.class, m.selfAssessmentId()));
        e.setAuditSource(reference(AuditSourceEntity.class, m.auditSourceId()));
        e.setExternalUrl(m.externalUrl());
        e.setNotes(m.notes());
        e.setStatus(m.status());
        e.setSubmittedBy(reference(Usuario.class, m.submittedBy()));
        e.setSubmittedAt(m.submittedAt());

        // A file is identified by its stored file: the same file is never attached twice.
        ChildCollections.sync(em, e.getFiles(), m.files(),
                f -> f.getStoredFile().getId(), EvidenceFile::storedFileId, EvidenceFileEntity::new,
                (file, entity) -> entity.setStoredFile(reference(StoredFileEntity.class, file.storedFileId())),
                e::addFile);

        // Reviews are history: existing ones are left as they are, new ones are added.
        ChildCollections.syncById(em, e.getReviews(), m.reviews(),
                EvidenceReviewEntity::getId, EvidenceReview::id, EvidenceReviewEntity::new,
                (review, entity) -> {
                    if (entity.getId() != null) return;
                    entity.setVerdict(review.verdict());
                    entity.setObservationType(reference(ObservationTypeEntity.class, review.observationTypeId()));
                    entity.setCommentText(review.comment());
                    entity.setReviewedBy(reference(Usuario.class, review.reviewedBy()));
                    entity.setReviewedAt(review.reviewedAt());
                },
                e::addReview);
    }

    @Override
    public Optional<EvidenceSubmission> findBySelfAssessmentIdAndAuditSourceId(Long selfAssessmentId, Long auditSourceId) {
        if (selfAssessmentId == null || auditSourceId == null) return Optional.empty();
        return first("where e.selfAssessment.id = :assessment and e.auditSource.id = :source",
                Map.of("assessment", selfAssessmentId, "source", auditSourceId));
    }

    @Override
    public List<EvidenceSubmission> findBySelfAssessmentId(Long selfAssessmentId) {
        if (selfAssessmentId == null) return List.of();
        return list("where e.selfAssessment.id = :assessment order by e.id", Map.of("assessment", selfAssessmentId));
    }

    @Override
    public Optional<EvidenceSubmission> findByStoredFileId(Long storedFileId) {
        if (storedFileId == null) return Optional.empty();
        return first("join e.files f where f.storedFile.id = :file", Map.of("file", storedFileId));
    }

    @Override
    public Page<EvidenceSubmission> findBySelfAssessmentIdAndStatus(Long selfAssessmentId, SubmissionStatus status,
                                                                    PageRequest pageRequest) {
        return page("where e.selfAssessment.id = :assessment and e.status = :status",
                Map.of("assessment", selfAssessmentId, "status", status), "e.submittedAt, e.id", pageRequest);
    }
}
