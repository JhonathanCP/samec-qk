package com.essalud.samec.assessment.application.port.in;

import java.util.List;

import com.essalud.samec.assessment.application.port.in.AssessmentViews.EvidenceView;
import com.essalud.samec.assessment.domain.model.ReviewVerdict;
import com.essalud.samec.assessment.domain.model.SubmissionStatus;
import com.essalud.samec.filestorage.application.port.in.FileStorageUseCases.NewFile;
import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;

/**
 * Evidence of the audit sources: the evaluatee submits and corrects it, the evaluator approves or
 * observes it.
 */
public interface EvidenceUseCases {

    record SubmitEvidence(Long selfAssessmentId, Long auditSourceId, List<NewFile> files, String externalUrl,
                          String notes) {
    }

    record ReviewEvidence(ReviewVerdict verdict, Long observationTypeId, String comment) {
    }

    // ── Evaluatee ──

    /** First submission for a source: one or more files and/or a link. */
    EvidenceView submit(SubmitEvidence command);

    EvidenceView addFiles(Long submissionId, List<NewFile> files);

    /** Removes the file from the evidence and deletes it. */
    EvidenceView removeFile(Long submissionId, Long storedFileId);

    EvidenceView updateDetails(Long submissionId, String externalUrl, String notes);

    /** Sends an observed evidence, once corrected, back to review. */
    EvidenceView resubmit(Long submissionId);

    /** Withdraws an evidence that is not approved, with its files. */
    void withdraw(Long submissionId);

    // ── Evaluator ──

    EvidenceView review(Long submissionId, ReviewEvidence command);

    // ── Both ──

    EvidenceView get(Long submissionId);

    /** Evidence of an assessment in a status (e.g. the evaluator's queue: PENDING_REVIEW). */
    Page<EvidenceView> listByStatus(Long selfAssessmentId, SubmissionStatus status, PageRequest pageRequest);
}
