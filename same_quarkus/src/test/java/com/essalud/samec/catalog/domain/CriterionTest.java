package com.essalud.samec.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.essalud.samec.catalog.domain.model.AuditSource;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.CriterionCondition;
import com.essalud.samec.catalog.domain.model.CriterionType;
import com.essalud.samec.catalog.domain.model.EvaluationTechnique;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.catalog.domain.model.SourceAvailability;
import com.essalud.samec.catalog.domain.model.SourceLogic;

class CriterionTest {

    private static AuditSource source(SourceAvailability availability) {
        String url = availability == SourceAvailability.PREDEFINED ? "https://essalud.gob.pe/doc.pdf" : null;
        return new AuditSource(null, "Plan anual", url, availability, false, 1);
    }

    private static CriterionCondition condition(int weight, EvaluationTechnique technique, AuditSource... sources) {
        return new CriterionCondition(null, "Condición", weight, technique, null, null, null,
                sources.length > 1 ? SourceLogic.ALL : null, List.of(), List.of(sources), 1);
    }

    private static Criterion criterion(CriterionCondition... conditions) {
        return new Criterion(null, 1L, "DIR-1", "Enunciado", CriterionType.STRUCTURE,
                Set.of(FacilityCategory.I_1, FacilityCategory.II_1), List.of(), List.of(conditions));
    }

    @Test
    void conditionWeightsMustAddUpToMaxScore() {
        assertThatThrownBy(() -> criterion(condition(1, EvaluationTechnique.OBSERVATION)))
                .hasMessageContaining("suman 1");

        Criterion ok = criterion(condition(1, EvaluationTechnique.OBSERVATION),
                condition(1, EvaluationTechnique.DOCUMENT_REVIEW, source(SourceAvailability.PROVIDED_BY_FACILITY)));
        assertThat(ok.conditions()).hasSize(2);
    }

    @Test
    void onlyDocumentReviewHasSources() {
        assertThatThrownBy(() -> condition(2, EvaluationTechnique.INTERVIEW, source(SourceAvailability.PROVIDED_BY_FACILITY)))
                .hasMessageContaining("revisión de documentos");
    }

    @Test
    void severalSourcesNeedALogic() {
        assertThatThrownBy(() -> new CriterionCondition(null, "C", 2, EvaluationTechnique.DOCUMENT_REVIEW, null, null, null,
                null, List.of(), List.of(source(SourceAvailability.PROVIDED_BY_FACILITY), source(SourceAvailability.PREDEFINED)), 1))
                .hasMessageContaining("todas (Y) o alguna (O)");
    }

    @Test
    void predefinedSourceNeedsUrl() {
        assertThatThrownBy(() -> new AuditSource(null, "Directiva", null, SourceAvailability.PREDEFINED, true, 1))
                .hasMessageContaining("enlace de referencia");
    }

    @Test
    void criterionIsFullyPredefinedOnlyWhenEverySourceIsPredefined() {
        Criterion predefined = criterion(condition(2, EvaluationTechnique.DOCUMENT_REVIEW,
                source(SourceAvailability.PREDEFINED), source(SourceAvailability.PREDEFINED)));
        Criterion mixed = criterion(condition(2, EvaluationTechnique.DOCUMENT_REVIEW,
                source(SourceAvailability.PREDEFINED), source(SourceAvailability.PROVIDED_BY_FACILITY)));
        Criterion onSite = criterion(condition(2, EvaluationTechnique.OBSERVATION));

        assertThat(predefined.isFullyPredefined()).isTrue();
        assertThat(mixed.isFullyPredefined()).isFalse();
        assertThat(onSite.isFullyPredefined()).isFalse();
        assertThat(mixed.conditions().get(0).sourcesRequiringEvidence()).hasSize(1);
    }

    @Test
    void appliesToItsCategoriesOnly() {
        Criterion c = criterion(condition(2, EvaluationTechnique.OBSERVATION));
        assertThat(c.appliesTo(FacilityCategory.I_1)).isTrue();
        assertThat(c.appliesTo(FacilityCategory.III_1)).isFalse();
        assertThat(FacilityCategory.fromLabel(" ii-1 ")).isEqualTo(FacilityCategory.II_1);
    }
}
