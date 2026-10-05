package com.essalud.samec.assessment.infrastructure.persistence;

import com.essalud.samec.filestorage.infrastructure.persistence.StoredFileEntity;
import com.essalud.samec.shared.trazabilidad.EntidadAuditable;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A file of an evidence submission. Each stored file belongs to a single submission. */
@Getter
@Setter
@Entity
@Table(name = "evidence_files",
        uniqueConstraints = @UniqueConstraint(name = "uk_evidence_files_stored_file", columnNames = "stored_file_id"),
        indexes = @Index(name = "ix_evidence_files_submission_id", columnList = "evidence_submission_id"))
public class EvidenceFileEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_submission_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_evidence_files_submission"))
    private EvidenceSubmissionEntity submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stored_file_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_evidence_files_stored_file"))
    private StoredFileEntity storedFile;
}
