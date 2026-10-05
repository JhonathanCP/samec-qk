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

/** Photo or document taken by the evaluator during an on-site verification. */
@Getter
@Setter
@Entity
@Table(name = "condition_review_files",
        uniqueConstraints = @UniqueConstraint(name = "uk_condition_review_files_stored_file", columnNames = "stored_file_id"),
        indexes = @Index(name = "ix_condition_review_files_review_id", columnList = "condition_review_id"))
public class ConditionReviewFileEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condition_review_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_condition_review_files_review"))
    private ConditionReviewEntity conditionReview;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stored_file_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_condition_review_files_stored_file"))
    private StoredFileEntity storedFile;
}
