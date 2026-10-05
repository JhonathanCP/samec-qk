package com.essalud.samec.filestorage.domain.model;

import java.time.LocalDateTime;
import java.util.Set;

import com.essalud.samec.shared.domain.DomainException;

/**
 * Metadata of a file kept in the database. The bytes are stored apart ({@link FileContent}) so
 * listing files never loads them.
 */
public record StoredFile(Long id, String originalName, String contentType, long sizeBytes, String sha256,
                         Long uploadedBy, LocalDateTime uploadedAt) {

    public static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    public StoredFile {
        if (originalName == null || originalName.isBlank()) throw new DomainException("El nombre del archivo es obligatorio");
        if (originalName.length() > 255) throw new DomainException("El nombre del archivo es demasiado largo (máximo 255 caracteres)");
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new DomainException("Tipo de archivo no permitido: " + contentType);
        }
        if (sizeBytes <= 0) throw new DomainException("El archivo está vacío");
        if (sizeBytes > MAX_SIZE_BYTES) throw new DomainException("El archivo supera los " + MAX_SIZE_BYTES / (1024 * 1024) + " MB");
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) throw new DomainException("Huella SHA-256 inválida");
    }

    /** Metadata of a new file, taken from its content. Uploader and date are set when stored. */
    public static StoredFile describe(String originalName, String contentType, FileContent content) {
        return new StoredFile(null, originalName, contentType, content.size(), content.sha256(), null, null);
    }

    public boolean matches(FileContent content) {
        return sizeBytes == content.size() && sha256.equals(content.sha256());
    }
}
