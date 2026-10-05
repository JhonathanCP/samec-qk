package com.essalud.samec.assessment.application.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.essalud.samec.assessment.application.port.in.AssessmentViews.ConditionReviewView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.CriterionScoreView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.EvidenceReviewView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.EvidenceView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.FileView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.SelfAssessmentView;
import com.essalud.samec.assessment.application.port.out.FacilityDirectory;
import com.essalud.samec.assessment.application.port.out.FacilityDirectory.FacilityInfo;
import com.essalud.samec.assessment.application.port.out.UserDirectory;
import com.essalud.samec.assessment.domain.model.ConditionReview;
import com.essalud.samec.assessment.domain.model.CriterionScore;
import com.essalud.samec.assessment.domain.model.EvidenceFile;
import com.essalud.samec.assessment.domain.model.EvidenceReview;
import com.essalud.samec.assessment.domain.model.EvidenceSubmission;
import com.essalud.samec.assessment.domain.model.SelfAssessment;
import com.essalud.samec.assessment.domain.service.AccreditationClassification;
import com.essalud.samec.catalog.domain.model.AuditSource;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.ObservationType;
import com.essalud.samec.catalog.domain.port.out.CriterionRepository;
import com.essalud.samec.catalog.domain.port.out.ObservationTypeRepository;
import com.essalud.samec.filestorage.application.port.in.FileStorageUseCases;
import com.essalud.samec.filestorage.domain.model.StoredFile;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Turns domain models into views. Names of people, files and observation types are fetched in
 * bulk ({@link Lookups}) so a whole checklist costs a handful of queries, not one per row.
 */
@ApplicationScoped
public class ViewAssembler {

    @Inject
    FacilityDirectory facilities;

    @Inject
    UserDirectory users;

    @Inject
    FileStorageUseCases files;

    @Inject
    ObservationTypeRepository observationTypes;

    @Inject
    CriterionRepository criteria;

    /** Everything a batch of views needs, fetched at once. */
    public record Lookups(Map<Long, StoredFile> files, Map<Long, String> userNames, Map<Long, String> observationTypes) {

        String userName(Long id) {
            return id == null ? null : userNames.get(id);
        }

        List<FileView> fileViews(Collection<Long> storedFileIds) {
            return storedFileIds.stream().map(files::get).filter(Objects::nonNull).map(ViewAssembler::fileView).toList();
        }
    }

    public Lookups lookups(Collection<EvidenceSubmission> submissions, Collection<ConditionReview> reviews,
                           Collection<CriterionScore> scores) {
        Set<Long> fileIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (EvidenceSubmission s : submissions) {
            s.files().forEach(f -> fileIds.add(f.storedFileId()));
            userIds.add(s.submittedBy());
            s.reviews().forEach(r -> userIds.add(r.reviewedBy()));
        }
        for (ConditionReview r : reviews) {
            fileIds.addAll(r.attachmentFileIds());
            userIds.add(r.reviewedBy());
        }
        scores.forEach(s -> userIds.add(s.scoredBy()));
        Map<Long, StoredFile> storedFiles = fileIds.isEmpty() ? Map.of()
                : files.describe(fileIds).stream().collect(Collectors.toMap(StoredFile::id, Function.identity()));
        Map<Long, String> types = observationTypes.findAll().stream()
                .collect(Collectors.toMap(ObservationType::id, ObservationType::name));
        return new Lookups(storedFiles, users.fullNames(userIds), types);
    }

    // ── Self-assessments ──

    public SelfAssessmentView assessment(SelfAssessment a) {
        return assessments(List.of(a)).get(0);
    }

    public List<SelfAssessmentView> assessments(List<SelfAssessment> list) {
        Map<Long, FacilityInfo> facilityInfo = facilities.findAll(list.stream().map(SelfAssessment::facilityId).toList());
        Map<Long, String> names = users.fullNames(list.stream()
                .flatMap(a -> Stream.of(a.closedBy(), a.evidenceLockedBy())).toList());
        return list.stream().map(a -> {
            FacilityInfo f = facilityInfo.get(a.facilityId());
            AccreditationClassification classification =
                    a.finalScore() == null ? null : AccreditationClassification.of(a.finalScore());
            return new SelfAssessmentView(a.id(), a.facilityId(), f == null ? null : f.code(), f == null ? null : f.name(),
                    a.periodYear(), a.facilityCategory(), a.status(), a.finalScore(), classification,
                    classification == null ? null : classification.label(), a.closedAt(),
                    a.closedBy() == null ? null : names.get(a.closedBy()), a.isEvidenceLocked(), a.evidenceLockedAt(),
                    a.evidenceLockedBy() == null ? null : names.get(a.evidenceLockedBy()));
        }).toList();
    }

    // ── Evidence ──

    /** Single evidence: resolves its source and criterion too. */
    public EvidenceView evidence(EvidenceSubmission s) {
        Criterion criterion = criteria.findByAuditSourceId(s.auditSourceId()).orElse(null);
        AuditSource source = criterion == null ? null : criterion.conditions().stream()
                .flatMap(c -> c.sources().stream()).filter(x -> x.id().equals(s.auditSourceId())).findFirst().orElse(null);
        return evidence(s, source == null ? null : source.name(), criterion == null ? null : criterion.code(),
                lookups(List.of(s), List.of(), List.of()));
    }

    public EvidenceView evidence(EvidenceSubmission s, String sourceName, String criterionCode, Lookups l) {
        List<EvidenceReviewView> reviews = s.reviews().stream().map(r -> review(r, l)).toList();
        return new EvidenceView(s.id(), s.selfAssessmentId(), s.auditSourceId(), sourceName, criterionCode,
                s.externalUrl(), s.notes(), s.status(), l.userName(s.submittedBy()), s.submittedAt(),
                l.fileViews(s.files().stream().map(EvidenceFile::storedFileId).toList()), reviews);
    }

    private static EvidenceReviewView review(EvidenceReview r, Lookups l) {
        return new EvidenceReviewView(r.id(), r.verdict(), r.observationTypeId(),
                r.observationTypeId() == null ? null : l.observationTypes().get(r.observationTypeId()),
                r.comment(), l.userName(r.reviewedBy()), r.reviewedAt());
    }

    // ── Condition reviews and scores ──

    public ConditionReviewView conditionReview(ConditionReview r) {
        return conditionReview(r, lookups(List.of(), List.of(r), List.of()));
    }

    public ConditionReviewView conditionReview(ConditionReview r, Lookups l) {
        return new ConditionReviewView(r.id(), r.selfAssessmentId(), r.conditionId(), r.met(), r.comment(),
                l.fileViews(r.attachmentFileIds()), l.userName(r.reviewedBy()), r.reviewedAt());
    }

    public CriterionScoreView score(CriterionScore s) {
        Map<Long, String> names = s.scoredBy() == null ? Map.of() : users.fullNames(List.of(s.scoredBy()));
        return score(s, new Lookups(Map.of(), names, Map.of()));
    }

    public CriterionScoreView score(CriterionScore s, Lookups l) {
        return new CriterionScoreView(s.id(), s.selfAssessmentId(), s.criterionId(), s.score(), s.origin(),
                s.justification(), l.userName(s.scoredBy()), s.scoredAt());
    }

    static FileView fileView(StoredFile f) {
        return new FileView(f.id(), f.originalName(), f.contentType(), f.sizeBytes(), f.uploadedAt());
    }
}
