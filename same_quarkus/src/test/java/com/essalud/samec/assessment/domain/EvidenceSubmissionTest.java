package com.essalud.samec.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.essalud.samec.assessment.domain.model.CriterionScore;
import com.essalud.samec.assessment.domain.model.EvidenceFile;
import com.essalud.samec.assessment.domain.model.EvidenceReview;
import com.essalud.samec.assessment.domain.model.EvidenceSubmission;
import com.essalud.samec.assessment.domain.model.ReviewVerdict;
import com.essalud.samec.assessment.domain.model.ScoreOrigin;
import com.essalud.samec.assessment.domain.model.SelfAssessment;
import com.essalud.samec.assessment.domain.model.SubmissionStatus;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.shared.domain.DomainException;

class EvidenceSubmissionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 10, 0);
    private static final Long EVALUATEE = 10L;
    private static final Long EVALUATOR = 20L;

    private static EvidenceSubmission submissionWithFiles(long... storedFileIds) {
        List<EvidenceFile> files = java.util.Arrays.stream(storedFileIds).mapToObj(id -> new EvidenceFile(null, id)).toList();
        return EvidenceSubmission.submit(1L, 5L, files, null, null, EVALUATEE, NOW);
    }

    @Test
    void acceptsOneOrMoreFiles() {
        EvidenceSubmission submission = submissionWithFiles(100L, 101L, 102L);
        assertThat(submission.files()).extracting(EvidenceFile::storedFileId).containsExactly(100L, 101L, 102L);
        assertThat(submission.status()).isEqualTo(SubmissionStatus.PENDING_REVIEW);
    }

    @Test
    void needsAtLeastOneFileOrLink() {
        assertThatThrownBy(() -> EvidenceSubmission.submit(1L, 5L, List.of(), " ", null, EVALUATEE, NOW))
                .hasMessageContaining("al menos un archivo o un enlace");

        EvidenceSubmission linkOnly = EvidenceSubmission.submit(1L, 5L, List.of(), "https://drive/x", null, EVALUATEE, NOW);
        assertThat(linkOnly.files()).isEmpty();

        EvidenceSubmission oneFile = submissionWithFiles(100L);
        assertThatThrownBy(() -> oneFile.removeFile(100L)).hasMessageContaining("al menos un archivo");
    }

    @Test
    void observedEvidenceIsCorrectedAndReviewedAgain() {
        EvidenceSubmission submission = submissionWithFiles(100L);
        submission.review(new EvidenceReview(null, ReviewVerdict.OBSERVED, 3L, "Año incorrecto", EVALUATOR, NOW));
        assertThat(submission.status()).isEqualTo(SubmissionStatus.OBSERVED);

        submission.addFile(new EvidenceFile(null, 101L));
        submission.removeFile(100L);
        submission.resubmit(EVALUATEE, NOW.plusDays(1));
        assertThat(submission.status()).isEqualTo(SubmissionStatus.PENDING_REVIEW);

        submission.review(new EvidenceReview(null, ReviewVerdict.APPROVED, null, null, EVALUATOR, NOW.plusDays(2)));
        assertThat(submission.status()).isEqualTo(SubmissionStatus.APPROVED);
        assertThat(submission.reviews()).hasSize(2);
        assertThat(submission.latestReview().verdict()).isEqualTo(ReviewVerdict.APPROVED);
        assertThatThrownBy(() -> submission.addFile(new EvidenceFile(null, 102L))).hasMessageContaining("aprobada");
    }

    @Test
    void observationNeedsItsType() {
        assertThatThrownBy(() -> new EvidenceReview(null, ReviewVerdict.OBSERVED, null, "x", EVALUATOR, NOW))
                .hasMessageContaining("tipo de observación");
    }

    @Test
    void cannotReviewTwiceWithoutResubmitting() {
        EvidenceSubmission submission = submissionWithFiles(100L);
        submission.review(new EvidenceReview(null, ReviewVerdict.OBSERVED, 3L, null, EVALUATOR, NOW));
        assertThatThrownBy(() -> submission.review(new EvidenceReview(null, ReviewVerdict.APPROVED, null, null, EVALUATOR, NOW)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void closedAssessmentRejectsChanges() {
        SelfAssessment assessment = SelfAssessment.start(1L, 2026, FacilityCategory.II_1);
        assessment.close(new BigDecimal("72.50"), EVALUATOR, NOW);
        assertThat(assessment.isOpen()).isFalse();
        assertThatThrownBy(assessment::ensureOpen).isInstanceOf(DomainException.class);
    }

    @Test
    void scoresStayInRange() {
        assertThatThrownBy(() -> new CriterionScore(null, 1L, 2L, 3, ScoreOrigin.MANUAL, null, EVALUATOR, NOW))
                .hasMessageContaining("fuera de rango");
        assertThatThrownBy(() -> new CriterionScore(null, 1L, 2L, 1, ScoreOrigin.MANUAL, null, null, NOW))
                .hasMessageContaining("evaluador");
        assertThat(CriterionScore.preScored(1L, 2L, NOW).score()).isEqualTo(2);
    }
}
