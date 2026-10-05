package com.essalud.samec.assessment.infrastructure.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;

import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.essalud.samec.filestorage.application.port.in.FileStorageUseCases.NewFile;
import com.essalud.samec.filestorage.domain.model.StoredFile;
import com.essalud.samec.shared.domain.DomainException;

/** Multipart uploads → files for the use cases (size checked before reading them into memory). */
final class Uploads {

    private Uploads() {
    }

    static List<NewFile> toNewFiles(List<FileUpload> uploads) {
        if (uploads == null) return List.of();
        return uploads.stream().filter(u -> u.size() > 0 || u.fileName() != null).map(Uploads::toNewFile).toList();
    }

    private static NewFile toNewFile(FileUpload upload) {
        if (upload.size() > StoredFile.MAX_SIZE_BYTES) {
            throw new DomainException("El archivo " + upload.fileName() + " supera los "
                    + StoredFile.MAX_SIZE_BYTES / (1024 * 1024) + " MB");
        }
        try {
            return new NewFile(upload.fileName(), Files.readAllBytes(upload.uploadedFile()));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", e);
        }
    }
}
