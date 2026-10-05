package com.essalud.samec.catalog.application.port.in;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import com.essalud.samec.catalog.domain.model.CriterionType;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.catalog.domain.model.MacroProcessCategory;

/**
 * The catalog as a light tree (macro process → standard → criterion summary), for the screen that
 * maintains it, and where institutional processes and normative documents are used.
 */
public interface CatalogTreeUseCase {

    record CriterionSummary(Long id, String code, String statement, CriterionType type,
                            Set<FacilityCategory> applicableCategories, int conditions, int sourcesToUpload,
                            int onSiteConditions, int weightTotal) {
    }

    record StandardNode(Long id, String code, String statement, List<CriterionSummary> criteria) {
    }

    record MacroProcessNode(Long id, String code, String name, MacroProcessCategory category, BigDecimal scoreWeight,
                            int displayOrder, List<StandardNode> standards) {
    }

    /** A condition of a criterion that uses an institutional process or cites a normative document. */
    record Usage(Long criterionId, String criterionCode, Long standardId, Long conditionId, String conditionDescription) {
    }

    /** Ordered: macro processes by display order; standards and criteria by code ("DIR1-2" before "DIR1-10"). */
    List<MacroProcessNode> tree();

    List<Usage> usagesOfInstitutionalProcess(Long institutionalProcessId);

    List<Usage> usagesOfNormativeDocument(Long normativeDocumentId);
}
