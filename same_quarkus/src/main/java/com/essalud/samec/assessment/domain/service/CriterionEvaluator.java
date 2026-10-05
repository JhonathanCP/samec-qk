package com.essalud.samec.assessment.domain.service;

import java.util.List;
import java.util.Map;

import com.essalud.samec.assessment.domain.model.ConditionReview;
import com.essalud.samec.assessment.domain.model.SubmissionStatus;
import com.essalud.samec.catalog.domain.model.AuditSource;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.CriterionCondition;
import com.essalud.samec.catalog.domain.model.SourceAvailability;
import com.essalud.samec.catalog.domain.model.SourceLogic;

/**
 * Derives, from what was submitted and reviewed, whether each condition of a criterion is met and
 * the score it suggests (the sum of the weights of the conditions met). The evaluator decides the
 * final score; this is the reference.
 */
public final class CriterionEvaluator {

    private CriterionEvaluator() {
    }

    /**
     * @param submissionStatusBySource status of the evidence of each audit source (absent = not submitted)
     * @param reviewByCondition        on-site review of each condition (absent = not verified)
     */
    public static ConditionStatus statusOf(CriterionCondition condition,
                                           Map<Long, SubmissionStatus> submissionStatusBySource,
                                           Map<Long, ConditionReview> reviewByCondition) {
        return statusOf(condition, submissionStatusBySource, reviewByCondition, false);
    }

    /**
     * @param evidenceClosed the document upload is closed: a source with no evidence is no longer
     *                       pending, it is not met
     */
    public static ConditionStatus statusOf(CriterionCondition condition,
                                           Map<Long, SubmissionStatus> submissionStatusBySource,
                                           Map<Long, ConditionReview> reviewByCondition, boolean evidenceClosed) {
        if (!condition.technique().isEvidenceBased()) {
            ConditionReview review = reviewByCondition.get(condition.id());
            if (review == null) return ConditionStatus.PENDING;
            return review.met() ? ConditionStatus.MET : ConditionStatus.NOT_MET;
        }
        List<ConditionStatus> sources = condition.sources().stream()
                .map(s -> statusOf(s, submissionStatusBySource.get(s.id()), evidenceClosed))
                .toList();
        if (sources.isEmpty()) return ConditionStatus.PENDING;
        if (condition.sourceLogic() == SourceLogic.ANY) {
            if (sources.contains(ConditionStatus.MET)) return ConditionStatus.MET;
            return sources.contains(ConditionStatus.PENDING) ? ConditionStatus.PENDING : ConditionStatus.NOT_MET;
        }
        if (sources.contains(ConditionStatus.NOT_MET)) return ConditionStatus.NOT_MET;
        return sources.contains(ConditionStatus.PENDING) ? ConditionStatus.PENDING : ConditionStatus.MET;
    }

    /** Status of one audit source given the status of its evidence (null when not submitted). */
    public static ConditionStatus statusOf(AuditSource source, SubmissionStatus submission) {
        return statusOf(source, submission, false);
    }

    public static ConditionStatus statusOf(AuditSource source, SubmissionStatus submission, boolean evidenceClosed) {
        if (evidenceClosed && source.availability() == SourceAvailability.PROVIDED_BY_FACILITY && submission == null) {
            return ConditionStatus.NOT_MET;
        }
        return switch (source.availability()) {
            case PREDEFINED -> ConditionStatus.MET;
            case NOT_IMPLEMENTED -> ConditionStatus.NOT_MET;
            case PROVIDED_BY_FACILITY -> {
                if (submission == null || submission == SubmissionStatus.PENDING_REVIEW) yield ConditionStatus.PENDING;
                yield submission == SubmissionStatus.APPROVED ? ConditionStatus.MET : ConditionStatus.NOT_MET;
            }
        };
    }

    /** Suggested score of the criterion; null while any condition is still pending. */
    public static Integer suggestedScore(Criterion criterion, Map<Long, SubmissionStatus> submissionStatusBySource,
                                         Map<Long, ConditionReview> reviewByCondition) {
        return suggestedScore(criterion, submissionStatusBySource, reviewByCondition, false);
    }

    public static Integer suggestedScore(Criterion criterion, Map<Long, SubmissionStatus> submissionStatusBySource,
                                         Map<Long, ConditionReview> reviewByCondition, boolean evidenceClosed) {
        if (criterion.conditions().isEmpty()) return null;
        int score = 0;
        for (CriterionCondition condition : criterion.conditions()) {
            ConditionStatus status = statusOf(condition, submissionStatusBySource, reviewByCondition, evidenceClosed);
            if (status == ConditionStatus.PENDING) return null;
            if (status == ConditionStatus.MET) score += condition.scoreWeight();
        }
        return score;
    }
}
