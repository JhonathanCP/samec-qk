package com.essalud.samec.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.essalud.samec.assessment.domain.service.AccreditationClassification;
import com.essalud.samec.assessment.domain.service.AccreditationScoring;
import com.essalud.samec.assessment.domain.service.AccreditationScoring.ScoredCriterion;
import com.essalud.samec.catalog.domain.model.CriterionType;
import com.essalud.samec.catalog.domain.model.MacroProcess;
import com.essalud.samec.catalog.domain.model.MacroProcessCategory;

class AccreditationScoringTest {

    private static final MacroProcess DIR = new MacroProcess(1L, "DIR", "Direccionamiento", MacroProcessCategory.MANAGEMENT,
            BigDecimal.valueOf(8), 1);
    private static final MacroProcess ATH = new MacroProcess(2L, "ATH", "Hospitalización", MacroProcessCategory.CARE_PROVISION,
            BigDecimal.valueOf(7), 2);
    private static final MacroProcess ADT = new MacroProcess(3L, "ADT", "Apoyo diagnóstico", MacroProcessCategory.SUPPORT,
            new BigDecimal("2.5"), 3);

    private static List<ScoredCriterion> criteria(Long macroId, CriterionType type, int... scores) {
        List<ScoredCriterion> list = new ArrayList<>();
        for (int score : scores) list.add(new ScoredCriterion((long) list.size(), macroId, type, score));
        return list;
    }

    /** Row DIR of "modelo excel acredita.xlsx": E 6 criteria (9 pts), P 4 (6 pts), R 1 (0 pts) → 61.76 %. */
    @Test
    void reproducesTheMacroProcessPercentageOfTheExcelModel() {
        List<ScoredCriterion> dir = new ArrayList<>();
        dir.addAll(criteria(1L, CriterionType.STRUCTURE, 2, 2, 2, 2, 1, 0));
        dir.addAll(criteria(1L, CriterionType.PROCESS, 2, 2, 1, 1));
        dir.addAll(criteria(1L, CriterionType.RESULT, 0));

        AccreditationScoring.Result result = AccreditationScoring.compute(List.of(DIR), dir);

        var macro = result.macroProcesses().get(0);
        assertThat(macro.weightedPoints()).isEqualTo(21);   // 9x1 + 6x2 + 0x3
        assertThat(macro.weightedMaximum()).isEqualTo(34);  // 12x1 + 8x2 + 2x3
        assertThat(macro.percentage()).isEqualByComparingTo("61.76");
        // Only macro process with criteria: it takes the whole weight
        assertThat(macro.effectiveWeight()).isEqualByComparingTo("100.00");
        assertThat(result.finalScore()).isEqualByComparingTo("61.76");
        assertThat(result.classification()).isEqualTo(AccreditationClassification.REMEDIATE_IN_9_MONTHS);
        assertThat(result.isComplete()).isTrue();
    }

    @Test
    void finalScoreWeighsEachMacroProcessAndSkipsTheOnesWithoutCriteria() {
        List<ScoredCriterion> all = new ArrayList<>();
        all.addAll(criteria(1L, CriterionType.STRUCTURE, 2, 2));  // DIR 100 %
        all.addAll(criteria(3L, CriterionType.PROCESS, 0, 0));    // ADT 0 %
        // ATH (hospitalization) does not apply to the facility: no criteria

        AccreditationScoring.Result result = AccreditationScoring.compute(List.of(DIR, ATH, ADT), all);

        // Weights 8 and 2.5 → 76.19 % and 23.81 %
        assertThat(result.finalScore()).isEqualByComparingTo("76.19");
        assertThat(result.macroProcesses()).filteredOn(m -> m.code().equals("ATH"))
                .singleElement().satisfies(m -> assertThat(m.effectiveWeight()).isEqualByComparingTo("0"));
        assertThat(result.categories()).filteredOn(c -> c.category() == MacroProcessCategory.MANAGEMENT)
                .singleElement().satisfies(c -> assertThat(c.percentage()).isEqualByComparingTo("100"));
    }

    @Test
    void unscoredCriteriaCountAsZeroAndMakeTheResultIncomplete() {
        List<ScoredCriterion> dir = List.of(
                new ScoredCriterion(1L, 1L, CriterionType.STRUCTURE, 2),
                new ScoredCriterion(2L, 1L, CriterionType.STRUCTURE, null));

        AccreditationScoring.Result result = AccreditationScoring.compute(List.of(DIR), dir);

        assertThat(result.isComplete()).isFalse();
        assertThat(result.scoredCount()).isEqualTo(1);
        assertThat(result.finalScore()).isEqualByComparingTo("50.00");
    }

    @Test
    void classificationThresholds() {
        assertThat(AccreditationClassification.of(new BigDecimal("49.99"))).isEqualTo(AccreditationClassification.NOT_APPROVED_RESTART);
        assertThat(AccreditationClassification.of(new BigDecimal("50"))).isEqualTo(AccreditationClassification.REMEDIATE_IN_9_MONTHS);
        assertThat(AccreditationClassification.of(new BigDecimal("69.99"))).isEqualTo(AccreditationClassification.REMEDIATE_IN_9_MONTHS);
        assertThat(AccreditationClassification.of(new BigDecimal("70"))).isEqualTo(AccreditationClassification.REMEDIATE_IN_6_MONTHS);
        assertThat(AccreditationClassification.of(new BigDecimal("84.99"))).isEqualTo(AccreditationClassification.REMEDIATE_IN_6_MONTHS);
        assertThat(AccreditationClassification.of(new BigDecimal("85"))).isEqualTo(AccreditationClassification.APPROVED);
    }
}
