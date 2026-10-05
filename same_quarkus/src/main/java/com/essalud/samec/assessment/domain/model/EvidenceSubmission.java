package com.essalud.samec.assessment.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.essalud.samec.shared.domain.DomainException;

/**
 * The evidence a facility gives for one auditable source within a self-assessment: one or more
 * files and/or an external link. The evaluator approves it or observes it; an observed
 * submission is corrected (files added or removed) and goes back to review.
 */
public class EvidenceSubmission {

    private final Long id;
    private final Long selfAssessmentId;
    private final Long auditSourceId;
    private String externalUrl;
    private String notes;
    private SubmissionStatus status;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private final List<EvidenceFile> files;
    private final List<EvidenceReview> reviews;

    public EvidenceSubmission(Long id, Long selfAssessmentId, Long auditSourceId, String externalUrl, String notes,
                              SubmissionStatus status, Long submittedBy, LocalDateTime submittedAt,
                              List<EvidenceFile> files, List<EvidenceReview> reviews) {
        if (selfAssessmentId == null) throw new DomainException("La autoevaluación es obligatoria");
        if (auditSourceId == null) throw new DomainException("La fuente auditable es obligatoria");
        this.id = id;
        this.selfAssessmentId = selfAssessmentId;
        this.auditSourceId = auditSourceId;
        this.externalUrl = blankToNull(externalUrl);
        this.notes = blankToNull(notes);
        this.status = status == null ? SubmissionStatus.PENDING_REVIEW : status;
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
        this.files = new ArrayList<>(files == null ? List.of() : files);
        this.reviews = new ArrayList<>(reviews == null ? List.of() : reviews);
        ensureHasContent();
    }

    public static EvidenceSubmission submit(Long selfAssessmentId, Long auditSourceId, List<EvidenceFile> files,
                                            String externalUrl, String notes, Long userId, LocalDateTime when) {
        return new EvidenceSubmission(null, selfAssessmentId, auditSourceId, externalUrl, notes,
                SubmissionStatus.PENDING_REVIEW, userId, when, files, List.of());
    }

    public void addFile(EvidenceFile file) {
        ensureEditable();
        files.add(file);
    }

    public void removeFile(Long storedFileId) {
        ensureEditable();
        boolean removed = files.removeIf(f -> f.storedFileId().equals(storedFileId));
        if (!removed) throw new DomainException("El archivo no pertenece a esta evidencia");
        ensureHasContent();
    }

    /** Changes the external link and the notes of the facility. */
    public void updateDetails(String externalUrl, String notes) {
        ensureEditable();
        this.externalUrl = blankToNull(externalUrl);
        this.notes = blankToNull(notes);
        ensureHasContent();
    }

    public boolean isApproved() {
        return status == SubmissionStatus.APPROVED;
    }

    /** Sends an observed submission, once corrected, back to the evaluator. */
    public void resubmit(Long userId, LocalDateTime when) {
        if (status != SubmissionStatus.OBSERVED) {
            throw new DomainException("Solo se reenvía una evidencia observada");
        }
        ensureHasContent();
        this.status = SubmissionStatus.PENDING_REVIEW;
        this.submittedBy = userId;
        this.submittedAt = when;
    }

    public void review(EvidenceReview review) {
        if (status != SubmissionStatus.PENDING_REVIEW) {
            throw new DomainException("Solo se puede revisar una evidencia pendiente de revisión");
        }
        reviews.add(review);
        this.status = review.verdict() == ReviewVerdict.APPROVED ? SubmissionStatus.APPROVED : SubmissionStatus.OBSERVED;
    }

    public EvidenceReview latestReview() {
        return reviews.isEmpty() ? null : reviews.get(reviews.size() - 1);
    }

    /** The facility can withdraw its evidence while it is not approved. */
    public void ensureWithdrawable() {
        ensureEditable();
    }

    private void ensureEditable() {
        if (status == SubmissionStatus.APPROVED) throw new DomainException("Una evidencia aprobada ya no se puede modificar");
    }

    private void ensureHasContent() {
        if (files.isEmpty() && externalUrl == null) {
            throw new DomainException("La evidencia debe tener al menos un archivo o un enlace");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public Long id() { return id; }
    public Long selfAssessmentId() { return selfAssessmentId; }
    public Long auditSourceId() { return auditSourceId; }
    public String externalUrl() { return externalUrl; }
    public String notes() { return notes; }
    public SubmissionStatus status() { return status; }
    public Long submittedBy() { return submittedBy; }
    public LocalDateTime submittedAt() { return submittedAt; }
    public List<EvidenceFile> files() { return Collections.unmodifiableList(files); }
    public List<EvidenceReview> reviews() { return Collections.unmodifiableList(reviews); }
}
