package com.essalud.samec.catalog.application.port.in;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.essalud.samec.catalog.domain.model.CriterionType;
import com.essalud.samec.catalog.domain.model.EvaluationTechnique;
import com.essalud.samec.catalog.domain.model.MacroProcessCategory;
import com.essalud.samec.catalog.domain.model.ScoreLevel;
import com.essalud.samec.catalog.domain.model.SourceAvailability;
import com.essalud.samec.catalog.domain.model.SourceLogic;

/**
 * Loads the LEA manual into the catalog (macro processes → standards → criteria with their
 * conditions, sources and normative references). Normative documents and institutional processes
 * are created once and shared. Everything goes in one transaction: if one criterion breaks a
 * domain rule nothing is saved.
 */
public interface LeaCatalogSeedUseCase {

    record LeaCatalog(String source, List<MacroProcessSeed> macroProcesses) {
    }

    record MacroProcessSeed(String code, String name, MacroProcessCategory category, BigDecimal scoreWeight,
                            int displayOrder, List<StandardSeed> standards) {
    }

    record StandardSeed(String code, String statement, List<CriterionSeed> criteria) {
    }

    /** {@code categories} as in the manual: "I-1" … "III-1". */
    record CriterionSeed(String code, String statement, CriterionType type, List<String> categories,
                         List<ScoreLevel> scoreLevels, List<ConditionSeed> conditions) {
    }

    /** {@code process} / {@code subprocess}: institutional process names (sub-process optional). */
    record ConditionSeed(String description, int scoreWeight, EvaluationTechnique technique, String process,
                         String subprocess, String facilityFunction, String responsibleRole, SourceLogic sourceLogic,
                         String verificationGuide, List<ReferenceSeed> normativeReferences, List<SourceSeed> sources,
                         int displayOrder) {
    }

    record ReferenceSeed(String title, String url, String applicableExcerpt) {
    }

    record SourceSeed(String name, String referenceUrl, SourceAvailability availability, boolean appliesSystemWide,
                      int displayOrder) {
    }

    record ObservationTypeSeed(String code, String name) {
    }

    record SeedResult(int macroProcesses, int standards, int criteria, int conditions, int normativeDocuments,
                      int institutionalProcesses) {
    }

    SeedResult load(LeaCatalog catalog);

    /** Loads it only when the catalog has no macro process yet. */
    Optional<SeedResult> loadIfEmpty(LeaCatalog catalog);

    /** Creates the observation types only when there is none yet; returns how many were created. */
    int loadObservationTypesIfEmpty(List<ObservationTypeSeed> types);
}
