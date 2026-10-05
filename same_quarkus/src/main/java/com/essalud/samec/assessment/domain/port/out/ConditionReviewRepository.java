package com.essalud.samec.assessment.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.essalud.samec.assessment.domain.model.ConditionReview;
import com.essalud.samec.shared.domain.port.CrudRepository;

/** Condition review aggregate: saving it also saves its attachments (condition_review_files). */
public interface ConditionReviewRepository extends CrudRepository<ConditionReview, Long> {

    Optional<ConditionReview> findBySelfAssessmentIdAndConditionId(Long selfAssessmentId, Long conditionId);

    List<ConditionReview> findBySelfAssessmentId(Long selfAssessmentId);

    /** The review a stored file (photo) is attached to. */
    Optional<ConditionReview> findByStoredFileId(Long storedFileId);
}
