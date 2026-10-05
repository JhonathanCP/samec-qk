package com.essalud.samec.assessment.domain.model;

import com.essalud.samec.shared.domain.DomainException;

/**
 * A file attached to an evidence submission. Name, size, uploader and date belong to the stored
 * file; the content lives in file storage.
 */
public record EvidenceFile(Long id, Long storedFileId) {

    public EvidenceFile {
        if (storedFileId == null) throw new DomainException("El archivo es obligatorio");
    }
}
