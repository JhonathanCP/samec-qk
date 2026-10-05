package com.essalud.samec.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.essalud.samec.assessment.domain.model.SelfAssessment;
import com.essalud.samec.assessment.domain.service.ConditionStatus;
import com.essalud.samec.assessment.domain.service.CriterionEvaluator;
import com.essalud.samec.catalog.domain.model.AuditSource;
import com.essalud.samec.catalog.domain.model.CriterionCondition;
import com.essalud.samec.catalog.domain.model.EvaluationTechnique;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.catalog.domain.model.SourceAvailability;
import com.essalud.samec.shared.domain.DomainException;

class EvidenceLockTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 9, 0);

    @Test
    void theEvaluatorClosesAndReopensTheUpload() {
        SelfAssessment a = SelfAssessment.start(1L, 2026, FacilityCategory.II_1);
        a.ensureEvidenceOpen();

        a.lockEvidence(20L, NOW);
        assertThat(a.isEvidenceLocked()).isTrue();
        assertThat(a.evidenceLockedBy()).isEqualTo(20L);
        assertThatThrownBy(a::ensureEvidenceOpen).isInstanceOf(DomainException.class).hasMessageContaining("cerrada por el evaluador");
        assertThatThrownBy(() -> a.lockEvidence(20L, NOW)).hasMessageContaining("ya está cerrada");

        a.unlockEvidence();
        assertThat(a.isEvidenceLocked()).isFalse();
        a.ensureEvidenceOpen();
        assertThatThrownBy(a::unlockEvidence).hasMessageContaining("ya está abierta");
    }

    @Test
    void aClosedAssessmentCannotBeLockedOrUnlocked() {
        SelfAssessment a = SelfAssessment.start(1L, 2026, FacilityCategory.II_1);
        a.close(BigDecimal.TEN, 20L, NOW);
        assertThatThrownBy(() -> a.lockEvidence(20L, NOW)).hasMessageContaining("está cerrada");
    }

    @Test
    void withTheUploadClosedAMissingSourceIsNotMetInsteadOfPending() {
        AuditSource source = new AuditSource(10L, "Plan", null, SourceAvailability.PROVIDED_BY_FACILITY, false, 1);
        CriterionCondition condition = new CriterionCondition(1L, "Plan", 2, EvaluationTechnique.DOCUMENT_REVIEW,
                null, null, null, null, List.of(), List.of(source), 1);

        assertThat(CriterionEvaluator.statusOf(condition, Map.of(), Map.of(), false)).isEqualTo(ConditionStatus.PENDING);
        assertThat(CriterionEvaluator.statusOf(condition, Map.of(), Map.of(), true)).isEqualTo(ConditionStatus.NOT_MET);
    }
}
