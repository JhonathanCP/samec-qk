package com.essalud.samec.assessment.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.essalud.samec.assessment.domain.model.AssessmentStatus;
import com.essalud.samec.assessment.domain.model.SelfAssessment;
import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;
import com.essalud.samec.shared.domain.port.CrudRepository;

public interface SelfAssessmentRepository extends CrudRepository<SelfAssessment, Long> {

    Optional<SelfAssessment> findByFacilityIdAndYear(Long facilityId, int periodYear);

    /** Most recent year first. */
    List<SelfAssessment> findByFacilityId(Long facilityId);

    /** Filters are optional (null = any); most recent year first. */
    Page<SelfAssessment> search(Long facilityId, Integer periodYear, AssessmentStatus status, PageRequest pageRequest);
}
