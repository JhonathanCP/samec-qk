package com.essalud.samec.assessment.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.essalud.samec.assessment.domain.model.AssessmentStatus;
import com.essalud.samec.assessment.domain.model.ReviewVerdict;
import com.essalud.samec.assessment.domain.model.ScoreOrigin;
import com.essalud.samec.assessment.domain.model.SubmissionStatus;
import com.essalud.samec.assessment.domain.service.AccreditationClassification;
import com.essalud.samec.assessment.domain.service.AccreditationScoring.CategoryResult;
import com.essalud.samec.assessment.domain.service.AccreditationScoring.MacroProcessResult;
import com.essalud.samec.assessment.domain.service.ConditionStatus;
import com.essalud.samec.catalog.domain.model.CriterionType;
import com.essalud.samec.catalog.domain.model.EvaluationTechnique;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.catalog.domain.model.MacroProcessCategory;
import com.essalud.samec.catalog.domain.model.ScoreLevel;
import com.essalud.samec.catalog.domain.model.SourceAvailability;
import com.essalud.samec.catalog.domain.model.SourceLogic;

/** Read models returned by the assessment use cases (ready to show: names instead of bare ids). */
public final class AssessmentViews {

    private AssessmentViews() {
    }

    public record FileView(Long id, String name, String contentType, long sizeBytes, LocalDateTime uploadedAt) {
    }

    public record SelfAssessmentView(Long id, Long facilityId, String facilityCode, String facilityName, int periodYear,
                                     FacilityCategory facilityCategory, AssessmentStatus status, BigDecimal finalScore,
                                     AccreditationClassification classification, String classificationLabel,
                                     LocalDateTime closedAt, String closedByName, boolean evidenceLocked,
                                     LocalDateTime evidenceLockedAt, String evidenceLockedByName) {
    }

    public record EvidenceReviewView(Long id, ReviewVerdict verdict, Long observationTypeId, String observationTypeName,
                                     String comment, String reviewedByName, LocalDateTime reviewedAt) {
    }

    public record EvidenceView(Long id, Long selfAssessmentId, Long auditSourceId, String auditSourceName,
                               String criterionCode, String externalUrl, String notes, SubmissionStatus status,
                               String submittedByName, LocalDateTime submittedAt, List<FileView> files,
                               List<EvidenceReviewView> reviews) {
    }

    public record ConditionReviewView(Long id, Long selfAssessmentId, Long conditionId, boolean met, String comment,
                                      List<FileView> attachments, String reviewedByName, LocalDateTime reviewedAt) {
    }

    public record CriterionScoreView(Long id, Long selfAssessmentId, Long criterionId, int score, ScoreOrigin origin,
                                     String justification, String scoredByName, LocalDateTime scoredAt) {
    }

    // ── Checklist: the LEA tree of the assessment with the state of every item ──

    public record ProgressView(int criteria, int criteriaScored, int sourcesRequiringEvidence, int sourcesSubmitted,
                               int sourcesPendingReview, int sourcesApproved, int sourcesObserved,
                               int onSiteConditions, int onSiteConditionsReviewed) {
    }

    public record ReferenceView(Long documentId, String title, String url, String applicableExcerpt) {
    }

    public record SourceNode(Long id, String name, String referenceUrl, SourceAvailability availability,
                             ConditionStatus status, EvidenceView evidence) {
    }

    public record ConditionNode(Long id, String description, int scoreWeight, EvaluationTechnique technique,
                                SourceLogic sourceLogic, String facilityFunction, String responsibleRole,
                                String verificationGuide, ConditionStatus status, List<ReferenceView> normativeReferences,
                                List<SourceNode> sources, ConditionReviewView review) {
    }

    public record CriterionNode(Long id, String code, String statement, CriterionType type, List<ScoreLevel> scoreLevels,
                                Integer suggestedScore, CriterionScoreView score, List<ConditionNode> conditions) {
    }

    public record StandardNode(Long id, String code, String statement, List<CriterionNode> criteria) {
    }

    public record MacroProcessNode(Long id, String code, String name, MacroProcessCategory category,
                                   List<StandardNode> standards) {
    }

    public record ChecklistView(SelfAssessmentView assessment, ProgressView progress,
                                List<MacroProcessNode> macroProcesses) {
    }

    // ── Result ──

    public record ResultView(SelfAssessmentView assessment, boolean complete, int criteriaCount, int scoredCount,
                             BigDecimal finalScore, AccreditationClassification classification,
                             String classificationLabel, List<CategoryResult> categories,
                             List<MacroProcessResult> macroProcesses) {
    }
}
