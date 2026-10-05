package com.essalud.samec.assessment.infrastructure.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.essalud.samec.assessment.domain.model.AssessmentStatus;
import com.essalud.samec.assessment.domain.model.SelfAssessment;
import com.essalud.samec.assessment.domain.port.out.SelfAssessmentRepository;
import com.essalud.samec.organizacion.Ipress;
import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;
import com.essalud.samec.shared.infrastructure.persistence.JpaCrudAdapter;
import com.essalud.samec.usuarios.Usuario;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SelfAssessmentJpaAdapter extends JpaCrudAdapter<SelfAssessment, SelfAssessmentEntity, Long>
        implements SelfAssessmentRepository {

    public SelfAssessmentJpaAdapter() {
        super(SelfAssessmentEntity.class, SelfAssessmentEntity::new, "Autoevaluación");
    }

    @Override
    protected Long idOf(SelfAssessment model) {
        return model.id();
    }

    @Override
    protected SelfAssessment toDomain(SelfAssessmentEntity e) {
        return new SelfAssessment(e.getId(), e.getFacility().getId(), e.getPeriodYear(), e.getFacilityCategory(),
                e.getStatus(), e.getFinalScore(), e.getClosedAt(),
                e.getClosedBy() == null ? null : e.getClosedBy().getId(), e.getEvidenceLockedAt(),
                e.getEvidenceLockedBy() == null ? null : e.getEvidenceLockedBy().getId());
    }

    @Override
    protected void copyToEntity(SelfAssessment m, SelfAssessmentEntity e) {
        e.setFacility(reference(Ipress.class, m.facilityId()));
        e.setPeriodYear(m.periodYear());
        e.setFacilityCategory(m.facilityCategory());
        e.setStatus(m.status());
        e.setFinalScore(m.finalScore());
        e.setClosedAt(m.closedAt());
        e.setClosedBy(reference(Usuario.class, m.closedBy()));
        e.setEvidenceLockedAt(m.evidenceLockedAt());
        e.setEvidenceLockedBy(reference(Usuario.class, m.evidenceLockedBy()));
    }

    @Override
    public Optional<SelfAssessment> findByFacilityIdAndYear(Long facilityId, int periodYear) {
        if (facilityId == null) return Optional.empty();
        return first("where e.facility.id = :facility and e.periodYear = :year",
                Map.of("facility", facilityId, "year", periodYear));
    }

    @Override
    public List<SelfAssessment> findByFacilityId(Long facilityId) {
        if (facilityId == null) return List.of();
        return list("where e.facility.id = :facility order by e.periodYear desc", Map.of("facility", facilityId));
    }

    @Override
    public Page<SelfAssessment> search(Long facilityId, Integer periodYear, AssessmentStatus status,
                                       PageRequest pageRequest) {
        StringBuilder where = new StringBuilder("where 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (facilityId != null) {
            where.append(" and e.facility.id = :facility");
            params.put("facility", facilityId);
        }
        if (periodYear != null) {
            where.append(" and e.periodYear = :year");
            params.put("year", periodYear);
        }
        if (status != null) {
            where.append(" and e.status = :status");
            params.put("status", status);
        }
        return page(where.toString(), params, "e.periodYear desc, e.id desc", pageRequest);
    }
}
