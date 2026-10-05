package com.essalud.samec.assessment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import com.essalud.samec.assessment.domain.model.ReviewVerdict;
import com.essalud.samec.assessment.domain.model.ScoreOrigin;
import com.essalud.samec.assessment.domain.model.SubmissionStatus;
import com.essalud.samec.assessment.infrastructure.persistence.CriterionScoreEntity;
import com.essalud.samec.assessment.infrastructure.persistence.EvidenceFileEntity;
import com.essalud.samec.assessment.infrastructure.persistence.EvidenceReviewEntity;
import com.essalud.samec.assessment.infrastructure.persistence.EvidenceSubmissionEntity;
import com.essalud.samec.assessment.infrastructure.persistence.SelfAssessmentEntity;
import com.essalud.samec.catalog.domain.model.CriterionType;
import com.essalud.samec.catalog.domain.model.EvaluationTechnique;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.catalog.domain.model.MacroProcessCategory;
import com.essalud.samec.catalog.domain.model.SourceAvailability;
import com.essalud.samec.catalog.domain.model.SourceLogic;
import com.essalud.samec.catalog.infrastructure.persistence.AuditSourceEntity;
import com.essalud.samec.catalog.infrastructure.persistence.ConditionNormativeReferenceEntity;
import com.essalud.samec.catalog.infrastructure.persistence.CriterionConditionEntity;
import com.essalud.samec.catalog.infrastructure.persistence.CriterionEntity;
import com.essalud.samec.catalog.infrastructure.persistence.CriterionScoreLevelEntity;
import com.essalud.samec.catalog.infrastructure.persistence.InstitutionalProcessEntity;
import com.essalud.samec.catalog.infrastructure.persistence.MacroProcessEntity;
import com.essalud.samec.catalog.infrastructure.persistence.NormativeDocumentEntity;
import com.essalud.samec.catalog.infrastructure.persistence.ObservationTypeEntity;
import com.essalud.samec.catalog.infrastructure.persistence.StandardEntity;
import com.essalud.samec.filestorage.infrastructure.persistence.StoredFileContentEntity;
import com.essalud.samec.filestorage.infrastructure.persistence.StoredFileEntity;
import com.essalud.samec.organizacion.Ipress;
import com.essalud.samec.organizacion.IpressRepository;
import com.essalud.samec.usuarios.Usuario;
import com.essalud.samec.usuarios.UsuarioRepository;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The core tables are created and their keys and constraints hold (H2 in tests). */
@QuarkusTest
class CoreSchemaPersistenceTest {

    @Inject EntityManager em;
    @Inject IpressRepository ipress;
    @Inject UsuarioRepository usuarios;

    private record Fixture(SelfAssessmentEntity assessment, CriterionEntity criterion, AuditSourceEntity source,
                           ObservationTypeEntity observationType, Usuario user) {
    }

    private Fixture fixture(String suffix) {
        MacroProcessEntity macro = new MacroProcessEntity();
        macro.setCode("DIR" + suffix);
        macro.setName("Direccionamiento");
        macro.setCategory(MacroProcessCategory.MANAGEMENT);
        macro.setScoreWeight(java.math.BigDecimal.TEN);
        macro.setDisplayOrder(1);
        em.persist(macro);

        StandardEntity standard = new StandardEntity();
        standard.setMacroProcess(macro);
        standard.setCode("DIR" + suffix + "-1");
        standard.setStatement("La organización cuenta con un plan estratégico...");
        em.persist(standard);

        InstitutionalProcessEntity process = new InstitutionalProcessEntity();
        process.setName("Gestión de la calidad " + suffix);
        em.persist(process);
        InstitutionalProcessEntity subProcess = new InstitutionalProcessEntity();
        subProcess.setParent(process);
        subProcess.setName("Acreditación");
        em.persist(subProcess);

        NormativeDocumentEntity norm = new NormativeDocumentEntity();
        norm.setTitle("NTS N° 050-MINSA/DGSP-V.02 " + suffix);
        norm.setUrl("https://www.gob.pe/nts-050");
        em.persist(norm);

        CriterionEntity criterion = new CriterionEntity();
        criterion.setStandard(standard);
        criterion.setCode("DIR" + suffix + "-1-1");
        criterion.setStatement("x".repeat(6000)); // long text column
        criterion.setCriterionType(CriterionType.STRUCTURE);
        criterion.getFacilityCategories().addAll(List.of(FacilityCategory.I_3, FacilityCategory.II_1));
        for (int score = 0; score <= 2; score++) {
            CriterionScoreLevelEntity level = new CriterionScoreLevelEntity();
            level.setScore(score);
            level.setDescription("Puntaje " + score);
            criterion.addScoreLevel(level);
        }

        CriterionConditionEntity documentary = new CriterionConditionEntity();
        documentary.setDescription("Cuenta con plan aprobado");
        documentary.setScoreWeight(1);
        documentary.setTechnique(EvaluationTechnique.DOCUMENT_REVIEW);
        documentary.setInstitutionalProcess(subProcess);
        documentary.setSourceLogic(SourceLogic.ALL);
        documentary.setDisplayOrder(1);
        AuditSourceEntity uploaded = new AuditSourceEntity();
        uploaded.setName("Plan anual aprobado con resolución");
        uploaded.setAvailability(SourceAvailability.PROVIDED_BY_FACILITY);
        uploaded.setDisplayOrder(1);
        documentary.addSource(uploaded);
        AuditSourceEntity predefined = new AuditSourceEntity();
        predefined.setName("Directiva institucional");
        predefined.setReferenceUrl("https://essalud.gob.pe/directiva.pdf");
        predefined.setAvailability(SourceAvailability.PREDEFINED);
        predefined.setAppliesSystemWide(true);
        predefined.setDisplayOrder(2);
        documentary.addSource(predefined);
        ConditionNormativeReferenceEntity reference = new ConditionNormativeReferenceEntity();
        reference.setNormativeDocument(norm);
        reference.setApplicableExcerpt("Numeral 6.2 ...");
        documentary.addNormativeReference(reference);
        criterion.addCondition(documentary);

        CriterionConditionEntity onSite = new CriterionConditionEntity();
        onSite.setDescription("El personal conoce el plan");
        onSite.setScoreWeight(1);
        onSite.setTechnique(EvaluationTechnique.INTERVIEW);
        onSite.setDisplayOrder(2);
        criterion.addCondition(onSite);
        em.persist(criterion);

        ObservationTypeEntity observationType = new ObservationTypeEntity();
        observationType.setCode("WRONG_YEAR" + suffix);
        observationType.setName("Documento de otro año");
        em.persist(observationType);

        Ipress facility = ipress.findAll().firstResult();
        Usuario user = usuarios.findAll().firstResult();
        SelfAssessmentEntity assessment = new SelfAssessmentEntity();
        assessment.setFacility(facility);
        assessment.setPeriodYear(2039); // a year no other test uses for the same facility
        assessment.setFacilityCategory(FacilityCategory.II_1);
        em.persist(assessment);

        em.flush();
        return new Fixture(assessment, criterion, uploaded, observationType, user);
    }

    private StoredFileEntity storeFile(String name, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        StoredFileEntity file = new StoredFileEntity();
        file.setOriginalName(name);
        file.setContentType("application/pdf");
        file.setSizeBytes((long) bytes.length);
        file.setSha256(new com.essalud.samec.filestorage.domain.model.FileContent(bytes).sha256());
        em.persist(file);
        StoredFileContentEntity content = new StoredFileContentEntity();
        content.setStoredFile(file);
        content.setContent(bytes);
        em.persist(content);
        return file;
    }

    private EvidenceSubmissionEntity submission(Fixture f) {
        EvidenceSubmissionEntity submission = new EvidenceSubmissionEntity();
        submission.setSelfAssessment(f.assessment());
        submission.setAuditSource(f.source());
        submission.setSubmittedBy(f.user());
        submission.setSubmittedAt(LocalDateTime.now());
        return submission;
    }

    /** Flushing must fail with a constraint violation (unique, FK or check). */
    private void assertViolatesConstraint(Object entity) {
        assertThatThrownBy(() -> {
            em.persist(entity); // IDENTITY ids: the INSERT runs here
            em.flush();
        }).satisfies(e -> {
            Throwable t = e;
            while (t != null && !(t instanceof ConstraintViolationException)) t = t.getCause();
            assertThat(t).as("constraint violation in %s", e).isNotNull();
        });
    }

    @Test
    @TestTransaction
    void evidenceKeepsSeveralFilesWithTheirBytesInTheDatabase() {
        Fixture f = fixture("A");
        EvidenceSubmissionEntity submission = submission(f);
        for (int i = 1; i <= 3; i++) {
            EvidenceFileEntity file = new EvidenceFileEntity();
            file.setStoredFile(storeFile("plan-" + i + ".pdf", "%PDF contenido " + i));
            submission.addFile(file);
        }
        EvidenceReviewEntity review = new EvidenceReviewEntity();
        review.setVerdict(ReviewVerdict.OBSERVED);
        review.setObservationType(f.observationType());
        review.setCommentText("Corresponde a 2024");
        review.setReviewedBy(f.user());
        review.setReviewedAt(LocalDateTime.now());
        submission.addReview(review);
        submission.setStatus(SubmissionStatus.OBSERVED);
        em.persist(submission);
        em.flush();
        em.clear();

        EvidenceSubmissionEntity loaded = em.find(EvidenceSubmissionEntity.class, submission.getId());
        assertThat(loaded.getFiles()).hasSize(3);
        assertThat(loaded.getReviews()).singleElement().extracting(EvidenceReviewEntity::getVerdict).isEqualTo(ReviewVerdict.OBSERVED);

        StoredFileEntity second = loaded.getFiles().get(1).getStoredFile();
        StoredFileContentEntity content = em.find(StoredFileContentEntity.class, second.getId());
        assertThat(new String(content.getContent(), StandardCharsets.UTF_8)).isEqualTo("%PDF contenido 2");
        assertThat(second.getCreatedAt()).isNotNull();

        CriterionEntity criterion = em.find(CriterionEntity.class, f.criterion().getId());
        assertThat(criterion.getStatement()).hasSize(6000);
        assertThat(criterion.getFacilityCategories()).containsExactlyInAnyOrder(FacilityCategory.I_3, FacilityCategory.II_1);
        assertThat(criterion.getScoreLevels()).extracting(CriterionScoreLevelEntity::getScore).containsExactly(0, 1, 2);
        assertThat(criterion.getConditions()).extracting(CriterionConditionEntity::getScoreWeight).containsExactly(1, 1);
        assertThat(criterion.getConditions().get(0).getSources()).hasSize(2);
        assertThat(criterion.getConditions().get(0).getNormativeReferences()).hasSize(1);
    }

    @Test
    @TestTransaction
    void oneSubmissionPerSourceAndAssessment() {
        Fixture f = fixture("B");
        EvidenceSubmissionEntity first = submission(f);
        first.setExternalUrl("https://drive/1");
        em.persist(first);
        em.flush();

        EvidenceSubmissionEntity duplicate = submission(f);
        duplicate.setExternalUrl("https://drive/2");
        assertViolatesConstraint(duplicate);
    }

    @Test
    @TestTransaction
    void aStoredFileBelongsToOneSubmissionOnly() {
        Fixture f = fixture("C");
        StoredFileEntity shared = storeFile("acta.pdf", "%PDF acta");
        EvidenceSubmissionEntity submission = submission(f);
        EvidenceFileEntity a = new EvidenceFileEntity();
        a.setStoredFile(shared);
        submission.addFile(a);
        EvidenceFileEntity b = new EvidenceFileEntity();
        b.setStoredFile(shared);
        submission.addFile(b);
        assertViolatesConstraint(submission);
    }

    @Test
    @TestTransaction
    void observedReviewWithoutTypeIsRejectedByTheDatabase() {
        Fixture f = fixture("D");
        EvidenceSubmissionEntity submission = submission(f);
        submission.setExternalUrl("https://drive/1");
        EvidenceReviewEntity review = new EvidenceReviewEntity();
        review.setVerdict(ReviewVerdict.OBSERVED);
        review.setReviewedBy(f.user());
        review.setReviewedAt(LocalDateTime.now());
        submission.addReview(review);
        assertViolatesConstraint(submission);
    }

    @Test
    @TestTransaction
    void scoreOutOfRangeIsRejectedByTheDatabase() {
        Fixture f = fixture("E");
        CriterionScoreEntity score = new CriterionScoreEntity();
        score.setSelfAssessment(f.assessment());
        score.setCriterion(f.criterion());
        score.setScore(3);
        score.setOrigin(ScoreOrigin.MANUAL);
        score.setScoredBy(f.user());
        score.setScoredAt(LocalDateTime.now());
        assertViolatesConstraint(score);
    }
}
