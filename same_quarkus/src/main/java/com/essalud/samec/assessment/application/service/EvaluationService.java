package com.essalud.samec.assessment.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.essalud.samec.assessment.application.port.in.AssessmentViews.ConditionReviewView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.CriterionScoreView;
import com.essalud.samec.assessment.application.port.in.EvaluationUseCases;
import com.essalud.samec.assessment.domain.model.ConditionReview;
import com.essalud.samec.assessment.domain.model.CriterionScore;
import com.essalud.samec.assessment.domain.model.EvidenceSubmission;
import com.essalud.samec.assessment.domain.model.ScoreOrigin;
import com.essalud.samec.assessment.domain.model.SelfAssessment;
import com.essalud.samec.assessment.domain.model.SubmissionStatus;
import com.essalud.samec.assessment.domain.port.out.ConditionReviewRepository;
import com.essalud.samec.assessment.domain.port.out.CriterionScoreRepository;
import com.essalud.samec.assessment.domain.port.out.EvidenceSubmissionRepository;
import com.essalud.samec.assessment.domain.service.CriterionEvaluator;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.CriterionCondition;
import com.essalud.samec.catalog.domain.port.out.CriterionRepository;
import com.essalud.samec.filestorage.application.port.in.FileStorageUseCases;
import com.essalud.samec.filestorage.domain.model.StoredFile;
import com.essalud.samec.shared.application.port.CurrentUser;
import com.essalud.samec.shared.domain.DomainException;
import com.essalud.samec.shared.error.RecursoNoEncontradoException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class EvaluationService implements EvaluationUseCases {

    @Inject ConditionReviewRepository conditionReviews;
    @Inject CriterionScoreRepository scores;
    @Inject EvidenceSubmissionRepository submissions;
    @Inject CriterionRepository criteria;
    @Inject FileStorageUseCases files;
    @Inject CurrentUser currentUser;
    @Inject AssessmentAccess access;
    @Inject ViewAssembler views;

    @Override
    public ConditionReviewView reviewCondition(ReviewCondition command) {
        SelfAssessment assessment = access.open(command.selfAssessmentId());
        Criterion criterion = criteria.findByConditionId(command.conditionId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Condición", command.conditionId()));
        access.ensureApplies(assessment, criterion);
        CriterionCondition condition = criterion.conditions().stream()
                .filter(c -> c.id().equals(command.conditionId())).findFirst().orElseThrow();
        if (condition.technique().isEvidenceBased()) {
            throw new DomainException("Esta condición se verifica con la evidencia de sus fuentes auditables, no en campo");
        }

        ConditionReview existing = conditionReviews
                .findBySelfAssessmentIdAndConditionId(assessment.id(), condition.id()).orElse(null);
        List<Long> attachments = new ArrayList<>(existing == null ? List.of() : existing.attachmentFileIds());
        if (command.attachments() != null) {
            files.storeAll(command.attachments()).stream().map(StoredFile::id).forEach(attachments::add);
        }
        ConditionReview saved = conditionReviews.save(new ConditionReview(existing == null ? null : existing.id(),
                assessment.id(), condition.id(), command.met(), blankToNull(command.comment()), attachments,
                currentUser.id(), LocalDateTime.now()));
        return views.conditionReview(saved);
    }

    @Override
    public ConditionReviewView removeConditionReviewFile(Long conditionReviewId, Long storedFileId) {
        ConditionReview review = conditionReviews.getById(conditionReviewId);
        access.open(review.selfAssessmentId());
        if (!review.attachmentFileIds().contains(storedFileId)) {
            throw new DomainException("El archivo no pertenece a esta verificación");
        }
        List<Long> remaining = review.attachmentFileIds().stream().filter(id -> !id.equals(storedFileId)).toList();
        ConditionReview saved = conditionReviews.save(new ConditionReview(review.id(), review.selfAssessmentId(),
                review.conditionId(), review.met(), review.comment(), remaining, review.reviewedBy(), review.reviewedAt()));
        files.delete(storedFileId);
        return views.conditionReview(saved);
    }

    @Override
    public CriterionScoreView scoreCriterion(ScoreCriterion command) {
        SelfAssessment assessment = access.open(command.selfAssessmentId());
        Criterion criterion = criteria.findById(command.criterionId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Criterio", command.criterionId()));
        access.ensureApplies(assessment, criterion);

        String justification = blankToNull(command.justification());
        Integer suggested = suggestedScore(assessment, criterion);
        if (suggested != null && suggested != command.score() && justification == null) {
            throw new DomainException("Según las evidencias el puntaje sugerido es " + suggested
                    + ": justifique por qué asigna " + command.score());
        }
        CriterionScore existing = scores.findBySelfAssessmentIdAndCriterionId(assessment.id(), criterion.id()).orElse(null);
        CriterionScore saved = scores.save(new CriterionScore(existing == null ? null : existing.id(), assessment.id(),
                criterion.id(), command.score(), ScoreOrigin.MANUAL, justification, currentUser.id(), LocalDateTime.now()));
        return views.score(saved);
    }

    private Integer suggestedScore(SelfAssessment assessment, Criterion criterion) {
        Map<Long, SubmissionStatus> statusBySource = submissions.findBySelfAssessmentId(assessment.id()).stream()
                .collect(Collectors.toMap(EvidenceSubmission::auditSourceId, EvidenceSubmission::status));
        Map<Long, ConditionReview> reviewByCondition = conditionReviews.findBySelfAssessmentId(assessment.id()).stream()
                .collect(Collectors.toMap(ConditionReview::conditionId, Function.identity()));
        return CriterionEvaluator.suggestedScore(criterion, statusBySource, reviewByCondition, assessment.isEvidenceLocked());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
