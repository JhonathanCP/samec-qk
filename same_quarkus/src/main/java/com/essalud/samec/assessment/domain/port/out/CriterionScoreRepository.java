package com.essalud.samec.assessment.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.essalud.samec.assessment.domain.model.CriterionScore;
import com.essalud.samec.shared.domain.port.CrudRepository;

public interface CriterionScoreRepository extends CrudRepository<CriterionScore, Long> {

    Optional<CriterionScore> findBySelfAssessmentIdAndCriterionId(Long selfAssessmentId, Long criterionId);

    List<CriterionScore> findBySelfAssessmentId(Long selfAssessmentId);
}
