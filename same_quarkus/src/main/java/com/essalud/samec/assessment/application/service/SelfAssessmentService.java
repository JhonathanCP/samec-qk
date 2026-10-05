package com.essalud.samec.assessment.application.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.essalud.samec.assessment.application.port.in.AssessmentViews.ChecklistView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.ConditionNode;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.CriterionNode;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.MacroProcessNode;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.ProgressView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.ReferenceView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.ResultView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.SelfAssessmentView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.SourceNode;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.StandardNode;
import com.essalud.samec.assessment.application.port.in.SelfAssessmentUseCases;
import com.essalud.samec.assessment.application.port.out.FacilityDirectory;
import com.essalud.samec.assessment.application.service.ViewAssembler.Lookups;
import com.essalud.samec.assessment.domain.model.AssessmentStatus;
import com.essalud.samec.assessment.domain.model.ConditionReview;
import com.essalud.samec.assessment.domain.model.CriterionScore;
import com.essalud.samec.assessment.domain.model.EvidenceSubmission;
import com.essalud.samec.assessment.domain.model.ScoreOrigin;
import com.essalud.samec.assessment.domain.model.SelfAssessment;
import com.essalud.samec.assessment.domain.model.SubmissionStatus;
import com.essalud.samec.assessment.domain.port.out.ConditionReviewRepository;
import com.essalud.samec.assessment.domain.port.out.CriterionScoreRepository;
import com.essalud.samec.assessment.domain.port.out.EvidenceSubmissionRepository;
import com.essalud.samec.assessment.domain.port.out.SelfAssessmentRepository;
import com.essalud.samec.assessment.domain.service.AccreditationClassification;
import com.essalud.samec.assessment.domain.service.AccreditationScoring;
import com.essalud.samec.assessment.domain.service.AccreditationScoring.ScoredCriterion;
import com.essalud.samec.assessment.domain.service.ConditionStatus;
import com.essalud.samec.assessment.domain.service.CriterionEvaluator;
import com.essalud.samec.catalog.domain.model.AuditSource;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.CriterionCondition;
import com.essalud.samec.catalog.domain.model.MacroProcess;
import com.essalud.samec.catalog.domain.model.NormativeDocument;
import com.essalud.samec.catalog.domain.model.Standard;
import com.essalud.samec.catalog.domain.port.out.CriterionRepository;
import com.essalud.samec.catalog.domain.port.out.MacroProcessRepository;
import com.essalud.samec.catalog.domain.port.out.NormativeDocumentRepository;
import com.essalud.samec.catalog.domain.port.out.StandardRepository;
import com.essalud.samec.shared.application.port.CurrentUser;
import com.essalud.samec.shared.domain.DomainException;
import com.essalud.samec.shared.domain.NaturalOrder;
import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;
import com.essalud.samec.shared.error.RecursoNoEncontradoException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class SelfAssessmentService implements SelfAssessmentUseCases {

    @Inject SelfAssessmentRepository assessments;
    @Inject EvidenceSubmissionRepository submissions;
    @Inject ConditionReviewRepository conditionReviews;
    @Inject CriterionScoreRepository scores;
    @Inject CriterionRepository criteria;
    @Inject StandardRepository standards;
    @Inject MacroProcessRepository macroProcesses;
    @Inject NormativeDocumentRepository normativeDocuments;
    @Inject FacilityDirectory facilities;
    @Inject CurrentUser currentUser;
    @Inject AssessmentAccess access;
    @Inject ViewAssembler views;

    // ── Start ──

    @Override
    public SelfAssessmentView start(StartSelfAssessment command) {
        currentUser.ensureCanAccessFacility(command.facilityId());
        if (facilities.find(command.facilityId()).isEmpty()) {
            throw RecursoNoEncontradoException.de("IPRESS", command.facilityId());
        }
        int currentYear = Year.now().getValue();
        if (command.periodYear() < 2000 || command.periodYear() > currentYear + 1) {
            throw new DomainException("El año debe estar entre 2000 y " + (currentYear + 1));
        }
        if (command.facilityCategory() == null) throw new DomainException("Indique la categoría de la IPRESS");
        if (assessments.findByFacilityIdAndYear(command.facilityId(), command.periodYear()).isPresent()) {
            throw new DomainException("La IPRESS ya tiene una autoevaluación del año " + command.periodYear());
        }
        List<Criterion> applicable = criteria.findApplicableTo(command.facilityCategory());
        if (applicable.isEmpty()) {
            throw new DomainException("No hay criterios de acreditación que apliquen a la categoría "
                    + command.facilityCategory().label() + ". Cargue primero el catálogo.");
        }

        SelfAssessment assessment = assessments.save(
                SelfAssessment.start(command.facilityId(), command.periodYear(), command.facilityCategory()));
        LocalDateTime now = LocalDateTime.now();
        applicable.stream()
                .filter(Criterion::isFullyPredefined)
                .forEach(c -> scores.save(CriterionScore.preScored(assessment.id(), c.id(), now)));
        return views.assessment(assessment);
    }

    // ── Queries ──

    @Override
    public Page<SelfAssessmentView> search(Long facilityId, Integer periodYear, AssessmentStatus status,
                                           PageRequest pageRequest) {
        Long facility = facilityId;
        if (!currentUser.hasNationalScope()) {
            if (facility == null) facility = currentUser.facilityId();
            if (facility == null) throw new DomainException("Indique la IPRESS");
            currentUser.ensureCanAccessFacility(facility);
        }
        Page<SelfAssessment> page = assessments.search(facility, periodYear, status, pageRequest);
        List<SelfAssessmentView> content = views.assessments(page.content());
        return new Page<>(content, page.totalElements(), page.totalPages(), page.page(), page.size());
    }

    @Override
    public SelfAssessmentView get(Long selfAssessmentId) {
        return views.assessment(access.visible(selfAssessmentId));
    }

    @Override
    public ChecklistView checklist(Long selfAssessmentId) {
        SelfAssessment assessment = access.visible(selfAssessmentId);
        List<Criterion> applicable = criteria.findApplicableTo(assessment.facilityCategory());

        List<EvidenceSubmission> submissionList = submissions.findBySelfAssessmentId(assessment.id());
        List<ConditionReview> reviewList = conditionReviews.findBySelfAssessmentId(assessment.id());
        List<CriterionScore> scoreList = scores.findBySelfAssessmentId(assessment.id());
        Map<Long, EvidenceSubmission> submissionBySource = submissionList.stream()
                .collect(Collectors.toMap(EvidenceSubmission::auditSourceId, Function.identity()));
        Map<Long, SubmissionStatus> statusBySource = submissionList.stream()
                .collect(Collectors.toMap(EvidenceSubmission::auditSourceId, EvidenceSubmission::status));
        Map<Long, ConditionReview> reviewByCondition = reviewList.stream()
                .collect(Collectors.toMap(ConditionReview::conditionId, Function.identity()));
        Map<Long, CriterionScore> scoreByCriterion = scoreList.stream()
                .collect(Collectors.toMap(CriterionScore::criterionId, Function.identity()));
        Map<Long, NormativeDocument> documents = normativeDocuments.findAllById(applicable.stream()
                        .flatMap(c -> c.conditions().stream())
                        .flatMap(c -> c.normativeReferences().stream())
                        .map(r -> r.documentId()).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(NormativeDocument::id, Function.identity()));
        Lookups lookups = views.lookups(submissionList, reviewList, scoreList);
        boolean cerrada = assessment.isEvidenceLocked();
        Counter counter = new Counter();

        Map<Long, List<CriterionNode>> criteriaByStandard = new LinkedHashMap<>();
        applicable.stream().sorted(Comparator.comparing(Criterion::code, NaturalOrder.INSTANCE)).forEach(criterion -> {
            List<ConditionNode> conditions = criterion.conditions().stream().map(condition -> {
                ConditionStatus status = CriterionEvaluator.statusOf(condition, statusBySource, reviewByCondition, cerrada);
                List<SourceNode> sources = condition.sources().stream().map(source -> {
                    EvidenceSubmission evidence = submissionBySource.get(source.id());
                    counter.source(source, evidence);
                    return new SourceNode(source.id(), source.name(), source.referenceUrl(), source.availability(),
                            CriterionEvaluator.statusOf(source, evidence == null ? null : evidence.status(), cerrada),
                            evidence == null ? null : views.evidence(evidence, source.name(), criterion.code(), lookups));
                }).toList();
                ConditionReview review = reviewByCondition.get(condition.id());
                counter.condition(condition, review);
                return new ConditionNode(condition.id(), condition.description(), condition.scoreWeight(),
                        condition.technique(), condition.sourceLogic(), condition.facilityFunction(),
                        condition.responsibleRole(), condition.verificationGuide(), status, references(condition, documents), sources,
                        review == null ? null : views.conditionReview(review, lookups));
            }).toList();
            CriterionScore score = scoreByCriterion.get(criterion.id());
            counter.criterion(score);
            criteriaByStandard.computeIfAbsent(criterion.standardId(), k -> new ArrayList<>())
                    .add(new CriterionNode(criterion.id(), criterion.code(), criterion.statement(), criterion.type(),
                            criterion.scoreLevels(),
                            CriterionEvaluator.suggestedScore(criterion, statusBySource, reviewByCondition, cerrada),
                            score == null ? null : views.score(score, lookups), conditions));
        });

        Map<Long, Standard> standardById = standards.findAllById(criteriaByStandard.keySet()).stream()
                .collect(Collectors.toMap(Standard::id, Function.identity()));
        Map<Long, List<StandardNode>> standardsByMacro = new LinkedHashMap<>();
        standardById.values().stream().sorted(Comparator.comparing(Standard::code, NaturalOrder.INSTANCE)).forEach(s ->
                standardsByMacro.computeIfAbsent(s.macroProcessId(), k -> new ArrayList<>())
                        .add(new StandardNode(s.id(), s.code(), s.statement(), criteriaByStandard.get(s.id()))));
        List<MacroProcessNode> tree = macroProcesses.findAllOrdered().stream()
                .filter(m -> standardsByMacro.containsKey(m.id()))
                .map(m -> new MacroProcessNode(m.id(), m.code(), m.name(), m.category(), standardsByMacro.get(m.id())))
                .toList();

        return new ChecklistView(views.assessment(assessment), counter.view(), tree);
    }

    @Override
    public ResultView result(Long selfAssessmentId) {
        SelfAssessment assessment = access.visible(selfAssessmentId);
        AccreditationScoring.Result result = compute(assessment);
        BigDecimal finalScore = assessment.isOpen() ? result.finalScore() : assessment.finalScore();
        AccreditationClassification classification = AccreditationClassification.of(finalScore);
        return new ResultView(views.assessment(assessment), result.isComplete(), result.criteriaCount(),
                result.scoredCount(), finalScore, classification, classification.label(), result.categories(),
                result.macroProcesses());
    }

    // ── Document upload lock ("candado") ──

    /** Justification of the scores set automatically when the upload is closed (and undone when reopened). */
    static final String LOCK_JUSTIFICATION = "Sin sustentos al cerrar la carga documental del evaluado";

    @Override
    public LockResult lockEvidence(Long selfAssessmentId) {
        SelfAssessment assessment = access.open(selfAssessmentId);
        LocalDateTime now = LocalDateTime.now();
        assessment.lockEvidence(currentUser.id(), now);
        SelfAssessment saved = assessments.save(assessment);

        Map<Long, SubmissionStatus> statusBySource = submissions.findBySelfAssessmentId(assessment.id()).stream()
                .collect(Collectors.toMap(EvidenceSubmission::auditSourceId, EvidenceSubmission::status));
        Map<Long, ConditionReview> reviewByCondition = conditionReviews.findBySelfAssessmentId(assessment.id()).stream()
                .collect(Collectors.toMap(ConditionReview::conditionId, Function.identity()));
        Set<Long> scored = scores.findBySelfAssessmentId(assessment.id()).stream()
                .map(CriterionScore::criterionId).collect(Collectors.toSet());
        int autoScored = 0;
        for (Criterion criterion : criteria.findApplicableTo(assessment.facilityCategory())) {
            boolean onlyDocuments = criterion.conditions().stream().allMatch(c -> c.technique().isEvidenceBased());
            boolean nothingSubmitted = criterion.conditions().stream()
                    .flatMap(c -> c.sourcesRequiringEvidence().stream())
                    .noneMatch(s -> statusBySource.containsKey(s.id()));
            if (scored.contains(criterion.id()) || !onlyDocuments || !nothingSubmitted) continue;
            Integer suggested = CriterionEvaluator.suggestedScore(criterion, statusBySource, reviewByCondition, true);
            if (suggested == null) continue;
            scores.save(new CriterionScore(null, assessment.id(), criterion.id(), suggested, ScoreOrigin.PRE_SCORED,
                    LOCK_JUSTIFICATION, null, now));
            autoScored++;
        }
        return new LockResult(views.assessment(saved), autoScored);
    }

    @Override
    public SelfAssessmentView unlockEvidence(Long selfAssessmentId) {
        SelfAssessment assessment = access.open(selfAssessmentId);
        assessment.unlockEvidence();
        SelfAssessment saved = assessments.save(assessment);
        scores.findBySelfAssessmentId(assessment.id()).stream()
                .filter(s -> s.origin() == ScoreOrigin.PRE_SCORED && LOCK_JUSTIFICATION.equals(s.justification()))
                .forEach(s -> scores.deleteById(s.id()));
        return views.assessment(saved);
    }

    // ── Close ──

    @Override
    public SelfAssessmentView close(Long selfAssessmentId) {
        SelfAssessment assessment = access.open(selfAssessmentId);
        AccreditationScoring.Result result = compute(assessment);
        if (!result.isComplete()) {
            int missing = result.criteriaCount() - result.scoredCount();
            throw new DomainException("Faltan calificar " + missing + " criterio(s) para cerrar la autoevaluación");
        }
        assessment.close(result.finalScore(), currentUser.id(), LocalDateTime.now());
        return views.assessment(assessments.save(assessment));
    }

    // ── Helpers ──

    private AccreditationScoring.Result compute(SelfAssessment assessment) {
        List<Criterion> applicable = criteria.findApplicableTo(assessment.facilityCategory());
        Map<Long, Long> macroByStandard = standards.findAllById(
                        applicable.stream().map(Criterion::standardId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Standard::id, Standard::macroProcessId));
        Map<Long, Integer> scoreByCriterion = scores.findBySelfAssessmentId(assessment.id()).stream()
                .collect(Collectors.toMap(CriterionScore::criterionId, CriterionScore::score));
        List<ScoredCriterion> scored = applicable.stream()
                .map(c -> new ScoredCriterion(c.id(), macroByStandard.get(c.standardId()), c.type(),
                        scoreByCriterion.get(c.id())))
                .filter(c -> c.macroProcessId() != null)
                .toList();
        List<MacroProcess> macros = macroProcesses.findAllOrdered();
        return AccreditationScoring.compute(macros, scored);
    }

    private static List<ReferenceView> references(CriterionCondition condition, Map<Long, NormativeDocument> documents) {
        return condition.normativeReferences().stream().map(r -> {
            NormativeDocument d = documents.get(r.documentId());
            return new ReferenceView(r.documentId(), d == null ? null : d.title(), d == null ? null : d.url(),
                    r.applicableExcerpt());
        }).toList();
    }

    /** Progress counters of the checklist. */
    private static final class Counter {
        int criteria, criteriaScored, sourcesRequiringEvidence, sourcesSubmitted, sourcesPendingReview,
                sourcesApproved, sourcesObserved, onSiteConditions, onSiteConditionsReviewed;

        void criterion(CriterionScore score) {
            criteria++;
            if (score != null) criteriaScored++;
        }

        void source(AuditSource source, EvidenceSubmission evidence) {
            if (!source.requiresFacilityEvidence()) return;
            sourcesRequiringEvidence++;
            if (evidence == null) return;
            sourcesSubmitted++;
            switch (evidence.status()) {
                case PENDING_REVIEW -> sourcesPendingReview++;
                case APPROVED -> sourcesApproved++;
                case OBSERVED -> sourcesObserved++;
            }
        }

        void condition(CriterionCondition condition, ConditionReview review) {
            if (condition.technique().isEvidenceBased()) return;
            onSiteConditions++;
            if (review != null) onSiteConditionsReviewed++;
        }

        ProgressView view() {
            return new ProgressView(criteria, criteriaScored, sourcesRequiringEvidence, sourcesSubmitted,
                    sourcesPendingReview, sourcesApproved, sourcesObserved, onSiteConditions, onSiteConditionsReviewed);
        }
    }
}
