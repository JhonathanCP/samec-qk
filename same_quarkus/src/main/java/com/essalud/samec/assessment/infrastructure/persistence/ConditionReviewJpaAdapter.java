package com.essalud.samec.assessment.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.essalud.samec.assessment.domain.model.ConditionReview;
import com.essalud.samec.assessment.domain.port.out.ConditionReviewRepository;
import com.essalud.samec.catalog.infrastructure.persistence.CriterionConditionEntity;
import com.essalud.samec.filestorage.infrastructure.persistence.StoredFileEntity;
import com.essalud.samec.shared.infrastructure.persistence.ChildCollections;
import com.essalud.samec.shared.infrastructure.persistence.JpaCrudAdapter;
import com.essalud.samec.usuarios.Usuario;

import jakarta.enterprise.context.ApplicationScoped;

/** On-site condition review with its attachments. */
@ApplicationScoped
public class ConditionReviewJpaAdapter extends JpaCrudAdapter<ConditionReview, ConditionReviewEntity, Long>
        implements ConditionReviewRepository {

    public ConditionReviewJpaAdapter() {
        super(ConditionReviewEntity.class, ConditionReviewEntity::new, "Verificación de condición");
    }

    @Override
    protected Long idOf(ConditionReview model) {
        return model.id();
    }

    @Override
    protected ConditionReview toDomain(ConditionReviewEntity e) {
        return new ConditionReview(e.getId(), e.getSelfAssessment().getId(), e.getCondition().getId(),
                Boolean.TRUE.equals(e.getMet()), e.getCommentText(),
                e.getAttachments().stream().map(a -> a.getStoredFile().getId()).toList(),
                e.getReviewedBy().getId(), e.getReviewedAt());
    }

    @Override
    protected void copyToEntity(ConditionReview m, ConditionReviewEntity e) {
        e.setSelfAssessment(reference(SelfAssessmentEntity.class, m.selfAssessmentId()));
        e.setCondition(reference(CriterionConditionEntity.class, m.conditionId()));
        e.setMet(m.met());
        e.setCommentText(m.comment());
        e.setReviewedBy(reference(Usuario.class, m.reviewedBy()));
        e.setReviewedAt(m.reviewedAt());

        ChildCollections.sync(em, e.getAttachments(), m.attachmentFileIds(),
                a -> a.getStoredFile().getId(), Function.identity(), ConditionReviewFileEntity::new,
                (fileId, entity) -> entity.setStoredFile(reference(StoredFileEntity.class, fileId)),
                e::addAttachment);
    }

    @Override
    public Optional<ConditionReview> findBySelfAssessmentIdAndConditionId(Long selfAssessmentId, Long conditionId) {
        if (selfAssessmentId == null || conditionId == null) return Optional.empty();
        return first("where e.selfAssessment.id = :assessment and e.condition.id = :condition",
                Map.of("assessment", selfAssessmentId, "condition", conditionId));
    }

    @Override
    public List<ConditionReview> findBySelfAssessmentId(Long selfAssessmentId) {
        if (selfAssessmentId == null) return List.of();
        return list("where e.selfAssessment.id = :assessment order by e.id", Map.of("assessment", selfAssessmentId));
    }

    @Override
    public Optional<ConditionReview> findByStoredFileId(Long storedFileId) {
        if (storedFileId == null) return Optional.empty();
        return first("join e.attachments a where a.storedFile.id = :file", Map.of("file", storedFileId));
    }
}
