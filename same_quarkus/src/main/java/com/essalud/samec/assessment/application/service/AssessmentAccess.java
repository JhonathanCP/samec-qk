package com.essalud.samec.assessment.application.service;

import com.essalud.samec.assessment.domain.model.SelfAssessment;
import com.essalud.samec.assessment.domain.port.out.SelfAssessmentRepository;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.shared.application.port.CurrentUser;
import com.essalud.samec.shared.domain.DomainException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Loads a self-assessment checking that the user's scope covers its facility. */
@ApplicationScoped
public class AssessmentAccess {

    @Inject
    SelfAssessmentRepository assessments;

    @Inject
    CurrentUser currentUser;

    /** 404 if it does not exist, 403 if it is of a facility out of the user's scope. */
    public SelfAssessment visible(Long selfAssessmentId) {
        SelfAssessment assessment = assessments.getById(selfAssessmentId);
        currentUser.ensureCanAccessFacility(assessment.facilityId());
        return assessment;
    }

    /** Same as {@link #visible} and, besides, still open to changes. */
    public SelfAssessment open(Long selfAssessmentId) {
        SelfAssessment assessment = visible(selfAssessmentId);
        assessment.ensureOpen();
        return assessment;
    }

    public void ensureApplies(SelfAssessment assessment, Criterion criterion) {
        if (!criterion.appliesTo(assessment.facilityCategory())) {
            throw new DomainException("El criterio " + criterion.code() + " no aplica a la categoría "
                    + assessment.facilityCategory().label());
        }
    }
}
