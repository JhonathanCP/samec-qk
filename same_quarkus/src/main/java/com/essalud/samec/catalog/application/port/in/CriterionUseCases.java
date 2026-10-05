package com.essalud.samec.catalog.application.port.in;

import java.util.List;

import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.shared.application.port.in.CrudUseCase;

/** Criteria with their conditions, sources and references (the whole aggregate). */
public interface CriterionUseCases extends CrudUseCase<Criterion, Long> {

    List<Criterion> listByStandard(Long standardId);

    List<Criterion> listApplicableTo(FacilityCategory category);
}
