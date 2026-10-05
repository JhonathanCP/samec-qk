package com.essalud.samec.filestorage.infrastructure.persistence;

import com.essalud.samec.shared.trazabilidad.EntidadAuditable;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Metadata of a file kept in the database. The bytes are in {@link StoredFileContentEntity}
 * (stored_file_contents), so queries over files never pull the BLOB. Uploader and date are the
 * audit columns created_by / created_at.
 */
@Getter
@Setter
@Entity
@Table(name = "stored_files",
        indexes = @Index(name = "ix_stored_files_sha256", columnList = "sha256"),
        check = @CheckConstraint(name = "ck_stored_files_size_bytes", constraint = "size_bytes > 0"))
public class StoredFileEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_name", length = 255, nullable = false)
    private String originalName;

    @Column(name = "content_type", length = 150, nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /** Hex SHA-256 of the content: integrity check and duplicate detection. */
    @Column(name = "sha256", length = 64, nullable = false)
    private String sha256;
}
