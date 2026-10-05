package com.essalud.samec.assessment.application.port.in;

import com.essalud.samec.assessment.application.port.in.AssessmentViews.ChecklistView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.ResultView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.SelfAssessmentView;
import com.essalud.samec.assessment.domain.model.AssessmentStatus;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;

/** Life cycle of a self-assessment: start, follow up (checklist and result) and close. */
public interface SelfAssessmentUseCases {

    record StartSelfAssessment(Long facilityId, int periodYear, FacilityCategory facilityCategory) {
    }

    /**
     * Opens the assessment of a facility for a year, with the category the facility has today.
     * Criteria whose sources are all predefined get the maximum score right away.
     */
    SelfAssessmentView start(StartSelfAssessment command);

    /** Within the user's scope; a facility-scoped user only sees their own facility. */
    Page<SelfAssessmentView> search(Long facilityId, Integer periodYear, AssessmentStatus status, PageRequest pageRequest);

    SelfAssessmentView get(Long selfAssessmentId);

    /** The LEA tree that applies to the facility, with evidence, reviews, suggested and given scores. */
    ChecklistView checklist(Long selfAssessmentId);

    /** Accreditation score as of now (final once closed). */
    ResultView result(Long selfAssessmentId);

    /** Result of closing the document upload: criteria scored automatically because they had no evidence. */
    record LockResult(SelfAssessmentView assessment, int autoScored) {
    }

    /**
     * The evaluator closes the document upload ("candado"): the evaluatee can no longer submit or
     * change evidence. Criteria that are only document review and got no evidence at all are scored
     * automatically (0, or the weight of their institutional sources), as samec-old did.
     */
    LockResult lockEvidence(Long selfAssessmentId);

    /** Opens the upload again and undoes the automatic scores of {@link #lockEvidence}. */
    SelfAssessmentView unlockEvidence(Long selfAssessmentId);

    /** Requires every applicable criterion scored; stores the final score. */
    SelfAssessmentView close(Long selfAssessmentId);
}
