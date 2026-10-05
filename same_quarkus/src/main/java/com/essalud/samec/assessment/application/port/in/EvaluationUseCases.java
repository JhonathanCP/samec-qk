package com.essalud.samec.assessment.application.port.in;

import java.util.List;

import com.essalud.samec.assessment.application.port.in.AssessmentViews.ConditionReviewView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.CriterionScoreView;
import com.essalud.samec.filestorage.application.port.in.FileStorageUseCases.NewFile;

/** What the evaluator records besides reviewing evidence: on-site checks and criterion scores. */
public interface EvaluationUseCases {

    record ReviewCondition(Long selfAssessmentId, Long conditionId, boolean met, String comment,
                           List<NewFile> attachments) {
    }

    record ScoreCriterion(Long selfAssessmentId, Long criterionId, int score, String justification) {
    }

    /**
     * Records (or corrects) the on-site verification of a condition (observation, interview,
     * medical record audit). New photos are added to the ones it already has.
     */
    ConditionReviewView reviewCondition(ReviewCondition command);

    ConditionReviewView removeConditionReviewFile(Long conditionReviewId, Long storedFileId);

    /**
     * Gives (or corrects) the score of a criterion. A score different from the suggested one
     * needs a justification.
     */
    CriterionScoreView scoreCriterion(ScoreCriterion command);
}
