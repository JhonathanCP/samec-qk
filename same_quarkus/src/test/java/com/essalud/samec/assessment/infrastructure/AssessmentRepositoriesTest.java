package com.essalud.samec.assessment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.essalud.samec.assessment.domain.model.ConditionReview;
import com.essalud.samec.assessment.domain.model.CriterionScore;
import com.essalud.samec.assessment.domain.model.EvidenceFile;
import com.essalud.samec.assessment.domain.model.EvidenceReview;
import com.essalud.samec.assessment.domain.model.EvidenceSubmission;
import com.essalud.samec.assessment.domain.model.ReviewVerdict;
import com.essalud.samec.assessment.domain.model.ScoreOrigin;
import com.essalud.samec.assessment.domain.model.SelfAssessment;
import com.essalud.samec.assessment.domain.model.SubmissionStatus;
import com.essalud.samec.assessment.domain.port.out.ConditionReviewRepository;
import com.essalud.samec.assessment.domain.port.out.CriterionScoreRepository;
import com.essalud.samec.assessment.domain.port.out.EvidenceSubmissionRepository;
import com.essalud.samec.assessment.domain.port.out.SelfAssessmentRepository;
import com.essalud.samec.catalog.domain.model.AuditSource;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.CriterionCondition;
import com.essalud.samec.catalog.domain.model.CriterionType;
import com.essalud.samec.catalog.domain.model.EvaluationTechnique;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.catalog.domain.model.MacroProcess;
import com.essalud.samec.catalog.domain.model.MacroProcessCategory;
import com.essalud.samec.catalog.domain.model.ObservationType;
import com.essalud.samec.catalog.domain.model.SourceAvailability;
import com.essalud.samec.catalog.domain.model.Standard;
import com.essalud.samec.catalog.domain.port.out.CriterionRepository;
import com.essalud.samec.catalog.domain.port.out.MacroProcessRepository;
import com.essalud.samec.catalog.domain.port.out.ObservationTypeRepository;
import com.essalud.samec.catalog.domain.port.out.StandardRepository;
import com.essalud.samec.filestorage.domain.model.FileContent;
import com.essalud.samec.filestorage.domain.model.StoredFile;
import com.essalud.samec.filestorage.domain.port.out.StoredFileRepository;
import com.essalud.samec.organizacion.IpressRepository;
import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;
import com.essalud.samec.usuarios.UsuarioRepository;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** CRUD of the assessment and file storage tables through the repository ports. */
@QuarkusTest
class AssessmentRepositoriesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 9, 0);

    @Inject MacroProcessRepository macroProcesses;
    @Inject StandardRepository standards;
    @Inject CriterionRepository criteria;
    @Inject ObservationTypeRepository observationTypes;
    @Inject SelfAssessmentRepository assessments;
    @Inject EvidenceSubmissionRepository submissions;
    @Inject ConditionReviewRepository conditionReviews;
    @Inject CriterionScoreRepository scores;
    @Inject StoredFileRepository files;
    @Inject IpressRepository ipress;
    @Inject UsuarioRepository usuarios;

    private record Fixture(SelfAssessment assessment, Criterion criterion, Long sourceId, Long interviewConditionId,
                           Long observationTypeId, Long userId) {
    }

    private Fixture fixture(String suffix, int year) {
        MacroProcess macro = macroProcesses.save(new MacroProcess(null, "A" + suffix, "Macro", MacroProcessCategory.CARE_PROVISION, BigDecimal.TEN, 1));
        Standard standard = standards.save(new Standard(null, macro.id(), "A" + suffix + "-1", "Estándar"));
        Criterion criterion = criteria.save(new Criterion(null, standard.id(), "A" + suffix + "-1-1", "Criterio",
                CriterionType.STRUCTURE, Set.of(FacilityCategory.II_1), List.of(), List.of(
                new CriterionCondition(null, "Documento", 1, EvaluationTechnique.DOCUMENT_REVIEW, null, null, null, null,
                        List.of(), List.of(new AuditSource(null, "Acta", null, SourceAvailability.PROVIDED_BY_FACILITY, false, 1)), 1),
                new CriterionCondition(null, "Entrevista", 1, EvaluationTechnique.INTERVIEW, null, null, null, null,
                        List.of(), List.of(), 2))));
        ObservationType wrongYear = observationTypes.save(new ObservationType(null, "A" + suffix + "-YEAR", "Otro año", true));
        Long facilityId = ipress.findAll().firstResult().getId();
        SelfAssessment assessment = assessments.save(SelfAssessment.start(facilityId, year, FacilityCategory.II_1));
        return new Fixture(assessment, criterion, criterion.conditions().get(0).sources().get(0).id(),
                criterion.conditions().get(1).id(), wrongYear.id(), usuarios.findAll().firstResult().getId());
    }

    private StoredFile pdf(String name, String text) {
        FileContent content = new FileContent(text.getBytes(StandardCharsets.UTF_8));
        return files.store(StoredFile.describe(name, "application/pdf", content), content);
    }

    @Test
    @TestTransaction
    void selfAssessmentCrud() {
        Fixture f = fixture("SA", 2031);
        Long facilityId = f.assessment().facilityId();

        assertThat(assessments.findByFacilityIdAndYear(facilityId, 2031)).map(SelfAssessment::id).contains(f.assessment().id());
        assessments.save(SelfAssessment.start(facilityId, 2032, FacilityCategory.II_1));
        assertThat(assessments.findByFacilityId(facilityId)).extracting(SelfAssessment::periodYear).containsSubsequence(2032, 2031);

        SelfAssessment closing = assessments.getById(f.assessment().id());
        closing.close(new BigDecimal("86.25"), f.userId(), NOW);
        assessments.save(closing);
        SelfAssessment closed = assessments.getById(f.assessment().id());
        assertThat(closed.isOpen()).isFalse();
        assertThat(closed.finalScore()).isEqualByComparingTo("86.25");
        assertThat(closed.closedBy()).isEqualTo(f.userId());
    }

    @Test
    @TestTransaction
    void filesAreStoredAsBlobs() {
        StoredFile stored = pdf("acta.pdf", "%PDF acta");
        assertThat(stored.id()).isNotNull();
        assertThat(stored.uploadedAt()).isNotNull();
        assertThat(files.findContent(stored.id())).map(c -> new String(c.bytes(), StandardCharsets.UTF_8)).contains("%PDF acta");
        assertThat(files.findBySha256(stored.sha256())).extracting(StoredFile::id).contains(stored.id());

        StoredFile renamed = files.save(new StoredFile(stored.id(), "acta-2026.pdf", stored.contentType(), stored.sizeBytes(),
                stored.sha256(), null, null));
        assertThat(renamed.originalName()).isEqualTo("acta-2026.pdf");

        FileContent other = new FileContent(new byte[] {1, 2, 3});
        assertThatThrownBy(() -> files.save(StoredFile.describe("x.pdf", "application/pdf", other)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> files.store(StoredFile.describe("x.pdf", "application/pdf", other),
                new FileContent(new byte[] {9}))).hasMessageContaining("do not match");

        files.deleteById(stored.id());
        assertThat(files.existsById(stored.id())).isFalse();
        assertThat(files.findContent(stored.id())).isEmpty();
    }

    @Test
    @TestTransaction
    void evidenceWithSeveralFilesGoesThroughReview() {
        Fixture f = fixture("EV", 2033);
        StoredFile plan = pdf("plan.pdf", "%PDF plan");
        StoredFile annex = pdf("anexo.pdf", "%PDF anexo");

        EvidenceSubmission submitted = submissions.save(EvidenceSubmission.submit(f.assessment().id(), f.sourceId(),
                List.of(new EvidenceFile(null, plan.id()), new EvidenceFile(null, annex.id())), null, "Plan 2026", f.userId(), NOW));
        assertThat(submitted.files()).extracting(EvidenceFile::storedFileId).containsExactly(plan.id(), annex.id());
        assertThat(submitted.files()).allSatisfy(file -> assertThat(file.id()).isNotNull());

        // The evaluator observes it
        EvidenceSubmission toReview = submissions.getById(submitted.id());
        toReview.review(new EvidenceReview(null, ReviewVerdict.OBSERVED, f.observationTypeId(), "Es de 2024", f.userId(), NOW.plusHours(1)));
        submissions.save(toReview);
        assertThat(submissions.findBySelfAssessmentIdAndStatus(f.assessment().id(), SubmissionStatus.OBSERVED, PageRequest.of(0, 10))
                .content()).extracting(EvidenceSubmission::id).containsExactly(submitted.id());

        // The facility replaces one file and submits again
        StoredFile corrected = pdf("plan-2026.pdf", "%PDF plan 2026");
        EvidenceSubmission correcting = submissions.getById(submitted.id());
        correcting.removeFile(plan.id());
        correcting.addFile(new EvidenceFile(null, corrected.id()));
        correcting.resubmit(f.userId(), NOW.plusDays(1));
        submissions.save(correcting);

        // The evaluator approves it
        EvidenceSubmission approving = submissions.getById(submitted.id());
        approving.review(new EvidenceReview(null, ReviewVerdict.APPROVED, null, null, f.userId(), NOW.plusDays(2)));
        submissions.save(approving);

        EvidenceSubmission result = submissions.findBySelfAssessmentIdAndAuditSourceId(f.assessment().id(), f.sourceId()).orElseThrow();
        assertThat(result.status()).isEqualTo(SubmissionStatus.APPROVED);
        assertThat(result.files()).extracting(EvidenceFile::storedFileId).containsExactlyInAnyOrder(annex.id(), corrected.id());
        assertThat(result.reviews()).extracting(EvidenceReview::verdict).containsExactly(ReviewVerdict.OBSERVED, ReviewVerdict.APPROVED);
        assertThat(result.reviews().get(0).observationTypeId()).isEqualTo(f.observationTypeId());

        Page<EvidenceSubmission> pending = submissions.findBySelfAssessmentIdAndStatus(f.assessment().id(),
                SubmissionStatus.PENDING_REVIEW, PageRequest.of(0, 10));
        assertThat(pending.totalElements()).isZero();
        assertThat(submissions.findBySelfAssessmentId(f.assessment().id())).hasSize(1);

        submissions.deleteById(submitted.id());
        assertThat(submissions.findBySelfAssessmentId(f.assessment().id())).isEmpty();
        assertThat(files.existsById(annex.id())).isTrue(); // the file itself is removed by the use case
    }

    @Test
    @TestTransaction
    void conditionReviewWithPhotos() {
        Fixture f = fixture("CR", 2034);
        StoredFile photo1 = files.store(StoredFile.describe("foto1.jpg", "image/jpeg", new FileContent(new byte[] {1})),
                new FileContent(new byte[] {1}));
        StoredFile photo2 = files.store(StoredFile.describe("foto2.jpg", "image/jpeg", new FileContent(new byte[] {2})),
                new FileContent(new byte[] {2}));

        ConditionReview review = conditionReviews.save(new ConditionReview(null, f.assessment().id(), f.interviewConditionId(),
                false, "El personal no conoce el plan", List.of(photo1.id(), photo2.id()), f.userId(), NOW));
        assertThat(review.attachmentFileIds()).containsExactly(photo1.id(), photo2.id());

        conditionReviews.save(new ConditionReview(review.id(), f.assessment().id(), f.interviewConditionId(),
                true, "Reentrevistado: cumple", List.of(photo2.id()), f.userId(), NOW.plusDays(1)));
        ConditionReview updated = conditionReviews.findBySelfAssessmentIdAndConditionId(f.assessment().id(), f.interviewConditionId())
                .orElseThrow();
        assertThat(updated.met()).isTrue();
        assertThat(updated.attachmentFileIds()).containsExactly(photo2.id());
        assertThat(conditionReviews.findBySelfAssessmentId(f.assessment().id())).hasSize(1);
    }

    @Test
    @TestTransaction
    void criterionScoresCrud() {
        Fixture f = fixture("CS", 2035);
        CriterionScore manual = scores.save(new CriterionScore(null, f.assessment().id(), f.criterion().id(), 1,
                ScoreOrigin.MANUAL, "Falta difusión", f.userId(), NOW));
        assertThat(scores.findBySelfAssessmentIdAndCriterionId(f.assessment().id(), f.criterion().id()))
                .map(CriterionScore::score).contains(1);

        scores.save(new CriterionScore(manual.id(), f.assessment().id(), f.criterion().id(), 2, ScoreOrigin.MANUAL,
                "Subsanado", f.userId(), NOW.plusDays(1)));
        assertThat(scores.findBySelfAssessmentId(f.assessment().id())).singleElement()
                .satisfies(s -> assertThat(s.score()).isEqualTo(2));

        scores.deleteById(manual.id());
        assertThat(scores.findBySelfAssessmentId(f.assessment().id())).isEmpty();
    }
}
