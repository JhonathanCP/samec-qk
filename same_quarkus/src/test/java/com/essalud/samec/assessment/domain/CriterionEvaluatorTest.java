package com.essalud.samec.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.essalud.samec.assessment.domain.model.ConditionReview;
import com.essalud.samec.assessment.domain.model.SubmissionStatus;
import com.essalud.samec.assessment.domain.service.ConditionStatus;
import com.essalud.samec.assessment.domain.service.CriterionEvaluator;
import com.essalud.samec.catalog.domain.model.AuditSource;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.CriterionCondition;
import com.essalud.samec.catalog.domain.model.CriterionType;
import com.essalud.samec.catalog.domain.model.EvaluationTechnique;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.catalog.domain.model.SourceAvailability;
import com.essalud.samec.catalog.domain.model.SourceLogic;

class CriterionEvaluatorTest {

    private static final AuditSource UPLOADED_A = new AuditSource(10L, "Plan", null, SourceAvailability.PROVIDED_BY_FACILITY, false, 1);
    private static final AuditSource UPLOADED_B = new AuditSource(11L, "Acta", null, SourceAvailability.PROVIDED_BY_FACILITY, false, 2);
    private static final AuditSource NOT_IMPLEMENTED = new AuditSource(12L, "Directiva", null, SourceAvailability.NOT_IMPLEMENTED, false, 3);

    private static CriterionCondition documentary(SourceLogic logic, AuditSource... sources) {
        return new CriterionCondition(1L, "Documentos", 1, EvaluationTechnique.DOCUMENT_REVIEW, null, null, null, logic,
                List.of(), List.of(sources), 1);
    }

    private static final CriterionCondition INTERVIEW = new CriterionCondition(2L, "Entrevista", 1,
            EvaluationTechnique.INTERVIEW, null, null, null, null, List.of(), List.of(), 2);

    @Test
    void allSourcesLogicNeedsEveryOneApproved() {
        CriterionCondition all = documentary(SourceLogic.ALL, UPLOADED_A, UPLOADED_B);
        assertThat(status(all, Map.of(10L, SubmissionStatus.APPROVED))).isEqualTo(ConditionStatus.PENDING);
        assertThat(status(all, Map.of(10L, SubmissionStatus.APPROVED, 11L, SubmissionStatus.OBSERVED)))
                .isEqualTo(ConditionStatus.NOT_MET);
        assertThat(status(all, Map.of(10L, SubmissionStatus.APPROVED, 11L, SubmissionStatus.APPROVED)))
                .isEqualTo(ConditionStatus.MET);
    }

    @Test
    void anySourceLogicNeedsOneApproved() {
        CriterionCondition any = documentary(SourceLogic.ANY, UPLOADED_A, NOT_IMPLEMENTED);
        assertThat(status(any, Map.of())).isEqualTo(ConditionStatus.PENDING);
        assertThat(status(any, Map.of(10L, SubmissionStatus.OBSERVED))).isEqualTo(ConditionStatus.NOT_MET);
        assertThat(status(any, Map.of(10L, SubmissionStatus.APPROVED))).isEqualTo(ConditionStatus.MET);
    }

    @Test
    void suggestedScoreAddsTheWeightsOfTheConditionsMet() {
        Criterion criterion = new Criterion(1L, 1L, "C-1", "Criterio", CriterionType.PROCESS, Set.of(FacilityCategory.I_1),
                List.of(), List.of(documentary(null, UPLOADED_A), INTERVIEW));
        LocalDateTime now = LocalDateTime.now();

        assertThat(CriterionEvaluator.suggestedScore(criterion, Map.of(10L, SubmissionStatus.APPROVED), Map.of()))
                .as("interview still pending").isNull();
        ConditionReview notMet = new ConditionReview(1L, 1L, 2L, false, "No conoce el plan", List.of(), 5L, now);
        assertThat(CriterionEvaluator.suggestedScore(criterion, Map.of(10L, SubmissionStatus.APPROVED), Map.of(2L, notMet)))
                .isEqualTo(1);
        ConditionReview met = new ConditionReview(1L, 1L, 2L, true, null, List.of(), 5L, now);
        assertThat(CriterionEvaluator.suggestedScore(criterion, Map.of(10L, SubmissionStatus.APPROVED), Map.of(2L, met)))
                .isEqualTo(2);
    }

    private static ConditionStatus status(CriterionCondition condition, Map<Long, SubmissionStatus> statuses) {
        return CriterionEvaluator.statusOf(condition, statuses, Map.of());
    }
}
