package com.essalud.samec.assessment.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.essalud.samec.assessment.domain.model.CriterionScore;
import com.essalud.samec.assessment.domain.port.out.CriterionScoreRepository;
import com.essalud.samec.catalog.infrastructure.persistence.CriterionEntity;
import com.essalud.samec.shared.infrastructure.persistence.JpaCrudAdapter;
import com.essalud.samec.usuarios.Usuario;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CriterionScoreJpaAdapter extends JpaCrudAdapter<CriterionScore, CriterionScoreEntity, Long>
        implements CriterionScoreRepository {

    public CriterionScoreJpaAdapter() {
        super(CriterionScoreEntity.class, CriterionScoreEntity::new, "Puntaje de criterio");
    }

    @Override
    protected Long idOf(CriterionScore model) {
        return model.id();
    }

    @Override
    protected CriterionScore toDomain(CriterionScoreEntity e) {
        return new CriterionScore(e.getId(), e.getSelfAssessment().getId(), e.getCriterion().getId(), e.getScore(),
                e.getOrigin(), e.getJustification(), e.getScoredBy() == null ? null : e.getScoredBy().getId(),
                e.getScoredAt());
    }

    @Override
    protected void copyToEntity(CriterionScore m, CriterionScoreEntity e) {
        e.setSelfAssessment(reference(SelfAssessmentEntity.class, m.selfAssessmentId()));
        e.setCriterion(reference(CriterionEntity.class, m.criterionId()));
        e.setScore(m.score());
        e.setOrigin(m.origin());
        e.setJustification(m.justification());
        e.setScoredBy(reference(Usuario.class, m.scoredBy()));
        e.setScoredAt(m.scoredAt());
    }

    @Override
    public Optional<CriterionScore> findBySelfAssessmentIdAndCriterionId(Long selfAssessmentId, Long criterionId) {
        if (selfAssessmentId == null || criterionId == null) return Optional.empty();
        return first("where e.selfAssessment.id = :assessment and e.criterion.id = :criterion",
                Map.of("assessment", selfAssessmentId, "criterion", criterionId));
    }

    @Override
    public List<CriterionScore> findBySelfAssessmentId(Long selfAssessmentId) {
        if (selfAssessmentId == null) return List.of();
        return list("where e.selfAssessment.id = :assessment order by e.criterion.code",
                Map.of("assessment", selfAssessmentId));
    }
}
