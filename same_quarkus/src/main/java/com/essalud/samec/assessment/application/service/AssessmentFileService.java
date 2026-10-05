package com.essalud.samec.assessment.application.service;

import com.essalud.samec.assessment.application.port.in.AssessmentFileUseCases;
import com.essalud.samec.assessment.domain.model.ConditionReview;
import com.essalud.samec.assessment.domain.model.EvidenceSubmission;
import com.essalud.samec.assessment.domain.port.out.ConditionReviewRepository;
import com.essalud.samec.assessment.domain.port.out.EvidenceSubmissionRepository;
import com.essalud.samec.filestorage.application.port.in.FileStorageUseCases;
import com.essalud.samec.filestorage.application.port.in.FileStorageUseCases.FileDownload;
import com.essalud.samec.shared.error.RecursoNoEncontradoException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** A file can be downloaded by whoever can see the assessment it belongs to. */
@ApplicationScoped
@Transactional
public class AssessmentFileService implements AssessmentFileUseCases {

    @Inject EvidenceSubmissionRepository submissions;
    @Inject ConditionReviewRepository conditionReviews;
    @Inject FileStorageUseCases files;
    @Inject AssessmentAccess access;

    @Override
    public FileDownload download(Long storedFileId) {
        Long assessmentId = submissions.findByStoredFileId(storedFileId).map(EvidenceSubmission::selfAssessmentId)
                .or(() -> conditionReviews.findByStoredFileId(storedFileId).map(ConditionReview::selfAssessmentId))
                .orElseThrow(() -> RecursoNoEncontradoException.de("Archivo", storedFileId));
        access.visible(assessmentId);
        return files.download(storedFileId);
    }
}
