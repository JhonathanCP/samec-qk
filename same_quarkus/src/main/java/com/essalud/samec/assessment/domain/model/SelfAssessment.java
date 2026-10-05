package com.essalud.samec.assessment.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.shared.domain.DomainException;

/**
 * Aggregate root: the self-assessment of one facility (IPRESS) for one year. The facility
 * category is copied when the assessment starts, so a later change of category does not alter
 * which criteria applied.
 */
public class SelfAssessment {

    private final Long id;
    private final Long facilityId;
    private final int periodYear;
    private final FacilityCategory facilityCategory;
    private AssessmentStatus status;
    private BigDecimal finalScore;
    private LocalDateTime closedAt;
    private Long closedBy;
    /** Set while the evaluator keeps the document upload closed ("candado"). */
    private LocalDateTime evidenceLockedAt;
    private Long evidenceLockedBy;

    public SelfAssessment(Long id, Long facilityId, int periodYear, FacilityCategory facilityCategory,
                          AssessmentStatus status, BigDecimal finalScore, LocalDateTime closedAt, Long closedBy) {
        if (facilityId == null) throw new DomainException("La IPRESS es obligatoria");
        if (periodYear < 2000) throw new DomainException("Año inválido: " + periodYear);
        if (facilityCategory == null) throw new DomainException("La categoría de la IPRESS es obligatoria");
        this.id = id;
        this.facilityId = facilityId;
        this.periodYear = periodYear;
        this.facilityCategory = facilityCategory;
        this.status = status == null ? AssessmentStatus.OPEN : status;
        this.finalScore = finalScore;
        this.closedAt = closedAt;
        this.closedBy = closedBy;
    }

    public SelfAssessment(Long id, Long facilityId, int periodYear, FacilityCategory facilityCategory,
                          AssessmentStatus status, BigDecimal finalScore, LocalDateTime closedAt, Long closedBy,
                          LocalDateTime evidenceLockedAt, Long evidenceLockedBy) {
        this(id, facilityId, periodYear, facilityCategory, status, finalScore, closedAt, closedBy);
        this.evidenceLockedAt = evidenceLockedAt;
        this.evidenceLockedBy = evidenceLockedBy;
    }

    public static SelfAssessment start(Long facilityId, int periodYear, FacilityCategory facilityCategory) {
        return new SelfAssessment(null, facilityId, periodYear, facilityCategory, AssessmentStatus.OPEN, null, null, null);
    }

    public boolean isOpen() {
        return status == AssessmentStatus.OPEN;
    }

    public void ensureOpen() {
        if (!isOpen()) throw new DomainException("La autoevaluación " + periodYear + " está cerrada");
    }

    // ── Lock of the document upload ("candado") ──

    public boolean isEvidenceLocked() {
        return evidenceLockedAt != null;
    }

    /** The evaluator closes the document upload: the evaluatee can no longer submit or change evidence. */
    public void lockEvidence(Long userId, LocalDateTime when) {
        ensureOpen();
        if (isEvidenceLocked()) throw new DomainException("La carga documental del evaluado ya está cerrada");
        this.evidenceLockedAt = when;
        this.evidenceLockedBy = userId;
    }

    public void unlockEvidence() {
        ensureOpen();
        if (!isEvidenceLocked()) throw new DomainException("La carga documental del evaluado ya está abierta");
        this.evidenceLockedAt = null;
        this.evidenceLockedBy = null;
    }

    /** For the actions of the evaluatee: the upload must be open. */
    public void ensureEvidenceOpen() {
        ensureOpen();
        if (isEvidenceLocked()) {
            throw new DomainException("La carga documental del evaluado está cerrada por el evaluador: ya no se pueden registrar ni modificar sustentos");
        }
    }

    public void close(BigDecimal score, Long userId, LocalDateTime when) {
        ensureOpen();
        if (score == null || score.signum() < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new DomainException("El puntaje final debe estar entre 0 y 100");
        }
        this.status = AssessmentStatus.CLOSED;
        this.finalScore = score;
        this.closedBy = userId;
        this.closedAt = when;
    }

    public Long id() { return id; }
    public Long facilityId() { return facilityId; }
    public int periodYear() { return periodYear; }
    public FacilityCategory facilityCategory() { return facilityCategory; }
    public AssessmentStatus status() { return status; }
    public BigDecimal finalScore() { return finalScore; }
    public LocalDateTime closedAt() { return closedAt; }
    public Long closedBy() { return closedBy; }
    public LocalDateTime evidenceLockedAt() { return evidenceLockedAt; }
    public Long evidenceLockedBy() { return evidenceLockedBy; }
}
