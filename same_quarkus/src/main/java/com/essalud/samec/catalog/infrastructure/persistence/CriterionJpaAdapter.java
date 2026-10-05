package com.essalud.samec.catalog.infrastructure.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.essalud.samec.catalog.domain.model.AuditSource;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.CriterionCondition;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.catalog.domain.model.NormativeReference;
import com.essalud.samec.catalog.domain.model.ScoreLevel;
import com.essalud.samec.catalog.domain.port.out.CriterionRepository;
import com.essalud.samec.shared.infrastructure.persistence.ChildCollections;
import com.essalud.samec.shared.infrastructure.persistence.JpaCrudAdapter;

import jakarta.enterprise.context.ApplicationScoped;

/** Criterion aggregate: the criterion and every child table, saved and loaded together. */
@ApplicationScoped
public class CriterionJpaAdapter extends JpaCrudAdapter<Criterion, CriterionEntity, Long> implements CriterionRepository {

    public CriterionJpaAdapter() {
        super(CriterionEntity.class, CriterionEntity::new, "Criterio");
    }

    @Override
    protected Long idOf(Criterion model) {
        return model.id();
    }

    // ── Entity → domain ──

    @Override
    protected Criterion toDomain(CriterionEntity e) {
        return new Criterion(e.getId(), e.getStandard().getId(), e.getCode(), e.getStatement(), e.getCriterionType(),
                Set.copyOf(e.getFacilityCategories()),
                e.getScoreLevels().stream()
                        .sorted(Comparator.comparing(CriterionScoreLevelEntity::getScore))
                        .map(l -> new ScoreLevel(l.getScore(), l.getDescription()))
                        .toList(),
                e.getConditions().stream()
                        .sorted(Comparator.comparing(CriterionConditionEntity::getDisplayOrder))
                        .map(this::conditionToDomain)
                        .toList());
    }

    private CriterionCondition conditionToDomain(CriterionConditionEntity c) {
        return new CriterionCondition(c.getId(), c.getDescription(), c.getScoreWeight(), c.getTechnique(),
                c.getInstitutionalProcess() == null ? null : c.getInstitutionalProcess().getId(),
                c.getFacilityFunction(), c.getResponsibleRole(), c.getSourceLogic(),
                c.getNormativeReferences().stream()
                        .map(r -> new NormativeReference(r.getNormativeDocument().getId(), r.getApplicableExcerpt()))
                        .toList(),
                c.getSources().stream()
                        .sorted(Comparator.comparing(AuditSourceEntity::getDisplayOrder))
                        .map(s -> new AuditSource(s.getId(), s.getName(), s.getReferenceUrl(), s.getAvailability(),
                                Boolean.TRUE.equals(s.getAppliesSystemWide()), s.getDisplayOrder()))
                        .toList(),
                c.getDisplayOrder(), c.getVerificationGuide());
    }

    // ── Domain → entity ──

    @Override
    protected void copyToEntity(Criterion m, CriterionEntity e) {
        e.setStandard(reference(StandardEntity.class, m.standardId()));
        e.setCode(m.code());
        e.setStatement(m.statement());
        e.setCriterionType(m.type());
        e.getFacilityCategories().retainAll(m.applicableCategories());
        e.getFacilityCategories().addAll(m.applicableCategories());

        ChildCollections.sync(em, e.getScoreLevels(), m.scoreLevels(),
                CriterionScoreLevelEntity::getScore, ScoreLevel::score, CriterionScoreLevelEntity::new,
                (level, entity) -> {
                    entity.setScore(level.score());
                    entity.setDescription(level.description());
                },
                e::addScoreLevel);

        ChildCollections.syncById(em, e.getConditions(), m.conditions(),
                CriterionConditionEntity::getId, CriterionCondition::id, CriterionConditionEntity::new,
                this::copyCondition, e::addCondition);
    }

    private void copyCondition(CriterionCondition c, CriterionConditionEntity ce) {
        ce.setDescription(c.description());
        ce.setScoreWeight(c.scoreWeight());
        ce.setTechnique(c.technique());
        ce.setInstitutionalProcess(reference(InstitutionalProcessEntity.class, c.institutionalProcessId()));
        ce.setFacilityFunction(c.facilityFunction());
        ce.setResponsibleRole(c.responsibleRole());
        ce.setSourceLogic(c.sourceLogic());
        ce.setDisplayOrder(c.displayOrder());
        ce.setVerificationGuide(c.verificationGuide());

        ChildCollections.syncById(em, ce.getSources(), c.sources(),
                AuditSourceEntity::getId, AuditSource::id, AuditSourceEntity::new,
                (s, se) -> {
                    se.setName(s.name());
                    se.setReferenceUrl(s.referenceUrl());
                    se.setAvailability(s.availability());
                    se.setAppliesSystemWide(s.appliesSystemWide());
                    se.setDisplayOrder(s.displayOrder());
                },
                ce::addSource);

        ChildCollections.sync(em, ce.getNormativeReferences(), c.normativeReferences(),
                r -> r.getNormativeDocument().getId(), NormativeReference::documentId,
                ConditionNormativeReferenceEntity::new,
                (r, re) -> {
                    re.setNormativeDocument(reference(NormativeDocumentEntity.class, r.documentId()));
                    re.setApplicableExcerpt(r.applicableExcerpt());
                },
                ce::addNormativeReference);
    }

    // ── Queries ──

    @Override
    public Optional<Criterion> findByCode(String code) {
        return code == null ? Optional.empty() : first("where e.code = :code", Map.of("code", code));
    }

    @Override
    public List<Criterion> findByStandardId(Long standardId) {
        if (standardId == null) return List.of();
        return list("where e.standard.id = :id order by e.code", Map.of("id", standardId));
    }

    @Override
    public List<Criterion> findApplicableTo(FacilityCategory category) {
        if (category == null) return List.of();
        return list("where :category member of e.facilityCategories order by e.code", Map.of("category", category));
    }

    @Override
    public Optional<Criterion> findByConditionId(Long conditionId) {
        if (conditionId == null) return Optional.empty();
        return first("join e.conditions c where c.id = :id", Map.of("id", conditionId));
    }

    @Override
    public List<Criterion> findByInstitutionalProcessId(Long institutionalProcessId) {
        if (institutionalProcessId == null) return List.of();
        return list("where exists (select 1 from CriterionConditionEntity c where c.criterion = e"
                + " and c.institutionalProcess.id = :id) order by e.code", Map.of("id", institutionalProcessId));
    }

    @Override
    public List<Criterion> findByNormativeDocumentId(Long normativeDocumentId) {
        if (normativeDocumentId == null) return List.of();
        return list("where exists (select 1 from ConditionNormativeReferenceEntity r where r.condition.criterion = e"
                + " and r.normativeDocument.id = :id) order by e.code", Map.of("id", normativeDocumentId));
    }

    @Override
    public Optional<Criterion> findByAuditSourceId(Long auditSourceId) {
        if (auditSourceId == null) return Optional.empty();
        return first("join e.conditions c join c.sources s where s.id = :id", Map.of("id", auditSourceId));
    }
}
