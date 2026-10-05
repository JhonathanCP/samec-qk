package com.essalud.samec.assessment.application.service;

import java.time.LocalDateTime;
import java.util.List;

import com.essalud.samec.assessment.application.port.in.AssessmentViews.EvidenceView;
import com.essalud.samec.assessment.application.port.in.EvidenceUseCases;
import com.essalud.samec.assessment.domain.model.EvidenceFile;
import com.essalud.samec.assessment.domain.model.EvidenceReview;
import com.essalud.samec.assessment.domain.model.EvidenceSubmission;
import com.essalud.samec.assessment.domain.model.ReviewVerdict;
import com.essalud.samec.assessment.domain.model.SelfAssessment;
import com.essalud.samec.assessment.domain.model.SubmissionStatus;
import com.essalud.samec.assessment.domain.port.out.EvidenceSubmissionRepository;
import com.essalud.samec.catalog.domain.model.AuditSource;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.ObservationType;
import com.essalud.samec.catalog.domain.model.SourceAvailability;
import com.essalud.samec.catalog.domain.port.out.CriterionRepository;
import com.essalud.samec.catalog.domain.port.out.ObservationTypeRepository;
import com.essalud.samec.filestorage.application.port.in.FileStorageUseCases;
import com.essalud.samec.filestorage.application.port.in.FileStorageUseCases.NewFile;
import com.essalud.samec.shared.application.port.CurrentUser;
import com.essalud.samec.shared.domain.DomainException;
import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;
import com.essalud.samec.shared.error.RecursoNoEncontradoException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class EvidenceService implements EvidenceUseCases {

    /** Files accepted in one upload (each one up to StoredFile.MAX_SIZE_BYTES). */
    static final int MAX_FILES_PER_UPLOAD = 10;

    @Inject EvidenceSubmissionRepository submissions;
    @Inject CriterionRepository criteria;
    @Inject ObservationTypeRepository observationTypes;
    @Inject FileStorageUseCases files;
    @Inject CurrentUser currentUser;
    @Inject AssessmentAccess access;
    @Inject ViewAssembler views;

    // ── Evaluatee ──

    @Override
    public EvidenceView submit(SubmitEvidence command) {
        SelfAssessment assessment = access.open(command.selfAssessmentId());
        assessment.ensureEvidenceOpen();
        Criterion criterion = criteria.findByAuditSourceId(command.auditSourceId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Fuente auditable", command.auditSourceId()));
        access.ensureApplies(assessment, criterion);
        AuditSource source = sourceOf(criterion, command.auditSourceId());
        if (!source.requiresFacilityEvidence()) {
            throw new DomainException("La fuente \"" + source.name() + "\" no requiere evidencia de la IPRESS ("
                    + (source.availability() == SourceAvailability.PREDEFINED ? "es un documento institucional predefinido"
                    : "aún no está implementada") + ")");
        }
        if (submissions.findBySelfAssessmentIdAndAuditSourceId(assessment.id(), source.id()).isPresent()) {
            throw new DomainException("Ya registró evidencia para esta fuente: agregue los archivos a la existente");
        }
        List<EvidenceFile> stored = store(command.files());
        EvidenceSubmission submission = EvidenceSubmission.submit(assessment.id(), source.id(), stored,
                command.externalUrl(), command.notes(), currentUser.id(), LocalDateTime.now());
        return views.evidence(submissions.save(submission));
    }

    @Override
    public EvidenceView addFiles(Long submissionId, List<NewFile> newFiles) {
        if (newFiles == null || newFiles.isEmpty()) throw new DomainException("Adjunte al menos un archivo");
        EvidenceSubmission submission = editableByEvaluatee(submissionId);
        store(newFiles).forEach(submission::addFile);
        return views.evidence(submissions.save(submission));
    }

    @Override
    public EvidenceView removeFile(Long submissionId, Long storedFileId) {
        EvidenceSubmission submission = editableByEvaluatee(submissionId);
        submission.removeFile(storedFileId);
        EvidenceSubmission saved = submissions.save(submission);
        files.delete(storedFileId);
        return views.evidence(saved);
    }

    @Override
    public EvidenceView updateDetails(Long submissionId, String externalUrl, String notes) {
        EvidenceSubmission submission = editableByEvaluatee(submissionId);
        submission.updateDetails(externalUrl, notes);
        return views.evidence(submissions.save(submission));
    }

    @Override
    public EvidenceView resubmit(Long submissionId) {
        EvidenceSubmission submission = editableByEvaluatee(submissionId);
        submission.resubmit(currentUser.id(), LocalDateTime.now());
        return views.evidence(submissions.save(submission));
    }

    @Override
    public void withdraw(Long submissionId) {
        EvidenceSubmission submission = editableByEvaluatee(submissionId);
        submission.ensureWithdrawable();
        submissions.deleteById(submission.id());
        submission.files().forEach(f -> files.delete(f.storedFileId()));
    }

    // ── Evaluator ──

    @Override
    public EvidenceView review(Long submissionId, ReviewEvidence command) {
        EvidenceSubmission submission = editable(submissionId);
        if (command.verdict() == null) throw new DomainException("Indique si aprueba u observa la evidencia");
        Long observationTypeId = null;
        if (command.verdict() == ReviewVerdict.OBSERVED) {
            observationTypeId = command.observationTypeId();
            ObservationType type = observationTypeId == null ? null : observationTypes.findById(observationTypeId).orElse(null);
            if (type == null || !type.active()) throw new DomainException("Indique un tipo de observación válido");
        }
        submission.review(new EvidenceReview(null, command.verdict(), observationTypeId, blankToNull(command.comment()),
                currentUser.id(), LocalDateTime.now()));
        return views.evidence(submissions.save(submission));
    }

    // ── Both ──

    @Override
    public EvidenceView get(Long submissionId) {
        EvidenceSubmission submission = submissions.getById(submissionId);
        access.visible(submission.selfAssessmentId());
        return views.evidence(submission);
    }

    @Override
    public Page<EvidenceView> listByStatus(Long selfAssessmentId, SubmissionStatus status, PageRequest pageRequest) {
        access.visible(selfAssessmentId);
        if (status == null) throw new DomainException("Indique el estado de las evidencias");
        return submissions.findBySelfAssessmentIdAndStatus(selfAssessmentId, status, pageRequest).map(views::evidence);
    }

    // ── Helpers ──

    /** For the evaluatee: besides, the document upload must not be closed by the evaluator. */
    private EvidenceSubmission editableByEvaluatee(Long submissionId) {
        EvidenceSubmission submission = submissions.getById(submissionId);
        access.open(submission.selfAssessmentId()).ensureEvidenceOpen();
        return submission;
    }

    /** The submission, if its assessment is visible to the user and still open. */
    private EvidenceSubmission editable(Long submissionId) {
        EvidenceSubmission submission = submissions.getById(submissionId);
        access.open(submission.selfAssessmentId());
        return submission;
    }

    private List<EvidenceFile> store(List<NewFile> newFiles) {
        List<NewFile> list = newFiles == null ? List.of() : newFiles;
        if (list.size() > MAX_FILES_PER_UPLOAD) {
            throw new DomainException("Puede subir hasta " + MAX_FILES_PER_UPLOAD + " archivos a la vez");
        }
        return files.storeAll(list).stream().map(f -> new EvidenceFile(null, f.id())).toList();
    }

    private static AuditSource sourceOf(Criterion criterion, Long auditSourceId) {
        return criterion.conditions().stream().flatMap(c -> c.sources().stream())
                .filter(s -> s.id().equals(auditSourceId)).findFirst()
                .orElseThrow(() -> RecursoNoEncontradoException.de("Fuente auditable", auditSourceId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
