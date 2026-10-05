package com.essalud.samec.assessment.application.port.in;

import com.essalud.samec.filestorage.application.port.in.FileStorageUseCases.FileDownload;

/** Download of evidence files and on-site photos, for whoever can see their assessment. */
public interface AssessmentFileUseCases {

    FileDownload download(Long storedFileId);
}
