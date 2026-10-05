package com.essalud.samec.assessment.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.essalud.samec.assessment.domain.model.EvidenceSubmission;
import com.essalud.samec.assessment.domain.model.SubmissionStatus;
import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;
import com.essalud.samec.shared.domain.port.CrudRepository;

/**
 * Evidence submission aggregate: saving it also saves its files (evidence_files) and its review
 * history (evidence_reviews).
 */
public interface EvidenceSubmissionRepository extends CrudRepository<EvidenceSubmission, Long> {

    Optional<EvidenceSubmission> findBySelfAssessmentIdAndAuditSourceId(Long selfAssessmentId, Long auditSourceId);

    List<EvidenceSubmission> findBySelfAssessmentId(Long selfAssessmentId);

    /** The submission a stored file is attached to. */
    Optional<EvidenceSubmission> findByStoredFileId(Long storedFileId);

    /** Evaluator's queue: submissions of an assessment in a status, oldest submission first. */
    Page<EvidenceSubmission> findBySelfAssessmentIdAndStatus(Long selfAssessmentId, SubmissionStatus status,
                                                             PageRequest pageRequest);
}
