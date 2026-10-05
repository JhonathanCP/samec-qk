package com.essalud.samec.catalog.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.shared.domain.port.CrudRepository;

/**
 * Criterion aggregate: saving a criterion also saves its score levels, facility categories,
 * conditions, audit sources and normative references (tables criterion_score_levels,
 * criterion_facility_categories, criterion_conditions, audit_sources,
 * condition_normative_references).
 */
public interface CriterionRepository extends CrudRepository<Criterion, Long> {

    Optional<Criterion> findByCode(String code);

    List<Criterion> findByStandardId(Long standardId);

    /** Criteria that apply to a facility category, ordered by code. */
    List<Criterion> findApplicableTo(FacilityCategory category);

    /** The criterion a condition belongs to. */
    Optional<Criterion> findByConditionId(Long conditionId);

    /** The criterion an audit source belongs to. */
    Optional<Criterion> findByAuditSourceId(Long auditSourceId);

    /** Criteria with some condition in this institutional process (or sub-process). */
    List<Criterion> findByInstitutionalProcessId(Long institutionalProcessId);

    /** Criteria with some condition that cites this normative document. */
    List<Criterion> findByNormativeDocumentId(Long normativeDocumentId);
}
