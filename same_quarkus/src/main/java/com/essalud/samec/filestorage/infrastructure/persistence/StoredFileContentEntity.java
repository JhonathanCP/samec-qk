package com.essalud.samec.filestorage.infrastructure.persistence;

import org.hibernate.Length;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Bytes of a stored file, one row per file sharing its id. Length.LONG32 maps to BLOB on Oracle
 * and BYTEA on PostgreSQL (unlike @Lob, which on PostgreSQL would use large objects / OID).
 */
@Getter
@Setter
@Entity
@Table(name = "stored_file_contents")
public class StoredFileContentEntity {

    @Id
    @Column(name = "stored_file_id")
    private Long storedFileId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stored_file_id", foreignKey = @ForeignKey(name = "fk_stored_file_contents_stored_file"))
    private StoredFileEntity storedFile;

    /** Lazy: loading the row (e.g. to delete it) does not bring the bytes. */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", length = Length.LONG32, nullable = false)
    private byte[] content;
}
