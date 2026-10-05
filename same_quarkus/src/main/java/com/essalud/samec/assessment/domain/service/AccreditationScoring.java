package com.essalud.samec.assessment.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.CriterionType;
import com.essalud.samec.catalog.domain.model.MacroProcess;
import com.essalud.samec.catalog.domain.model.MacroProcessCategory;

/**
 * Accreditation scoring model (recursos/modelo excel acredita.xlsx of the previous system):
 * <ol>
 *   <li>Each criterion counts its score (0..2) times the factor of its type: E x1, P x2, R x3.</li>
 *   <li>Macro process % = weighted points obtained / weighted maximum (2 x factor) x 100.</li>
 *   <li>Final % = sum of (macro process weight x macro process %) / 100. The weights add up to 100
 *       (GER 35, PRE 40, APO 25, split among their macro processes).</li>
 * </ol>
 * A macro process without applicable criteria (by the facility category) is left out and the
 * weights of the others are scaled up so they still add up to 100. An unscored criterion counts
 * as 0 (the final score is only taken when every criterion is scored).
 */
public final class AccreditationScoring {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private AccreditationScoring() {
    }

    /** A criterion to score: its macro process, its type and its score (null = not scored yet). */
    public record ScoredCriterion(Long criterionId, Long macroProcessId, CriterionType type, Integer score) {
    }

    public record MacroProcessResult(Long macroProcessId, String code, String name, MacroProcessCategory category,
                                     BigDecimal configuredWeight, BigDecimal effectiveWeight, int criteriaCount,
                                     int scoredCount, int weightedPoints, int weightedMaximum, BigDecimal percentage) {
    }

    public record CategoryResult(MacroProcessCategory category, BigDecimal weight, BigDecimal percentage) {
    }

    public record Result(List<MacroProcessResult> macroProcesses, List<CategoryResult> categories,
                         BigDecimal finalScore, AccreditationClassification classification,
                         int criteriaCount, int scoredCount) {

        public boolean isComplete() {
            return criteriaCount > 0 && scoredCount == criteriaCount;
        }
    }

    public static Result compute(List<MacroProcess> macroProcesses, List<ScoredCriterion> criteria) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (MacroProcess macro : macroProcesses) {
            if (criteria.stream().anyMatch(c -> c.macroProcessId().equals(macro.id()))) {
                totalWeight = totalWeight.add(macro.scoreWeight());
            }
        }

        List<MacroProcessResult> results = new ArrayList<>();
        Map<MacroProcessCategory, BigDecimal[]> byCategory = new EnumMap<>(MacroProcessCategory.class);
        BigDecimal finalScore = BigDecimal.ZERO;
        int criteriaCount = 0;
        int scoredCount = 0;

        for (MacroProcess macro : macroProcesses) {
            List<ScoredCriterion> own = criteria.stream().filter(c -> c.macroProcessId().equals(macro.id())).toList();
            int points = 0;
            int maximum = 0;
            int scored = 0;
            for (ScoredCriterion c : own) {
                int factor = c.type().scoreFactor();
                maximum += Criterion.MAX_SCORE * factor;
                if (c.score() != null) {
                    points += c.score() * factor;
                    scored++;
                }
            }
            criteriaCount += own.size();
            scoredCount += scored;

            BigDecimal percentage = maximum == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(points).multiply(HUNDRED).divide(BigDecimal.valueOf(maximum), 4, RoundingMode.HALF_UP);
            BigDecimal effectiveWeight = own.isEmpty() || totalWeight.signum() == 0 ? BigDecimal.ZERO
                    : macro.scoreWeight().multiply(HUNDRED).divide(totalWeight, 4, RoundingMode.HALF_UP);
            finalScore = finalScore.add(effectiveWeight.multiply(percentage).divide(HUNDRED, 6, RoundingMode.HALF_UP));

            BigDecimal[] acc = byCategory.computeIfAbsent(macro.category(), k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            acc[0] = acc[0].add(effectiveWeight);
            acc[1] = acc[1].add(effectiveWeight.multiply(percentage));

            results.add(new MacroProcessResult(macro.id(), macro.code(), macro.name(), macro.category(),
                    macro.scoreWeight(), round(effectiveWeight), own.size(), scored, points, maximum, round(percentage)));
        }

        List<CategoryResult> categories = new ArrayList<>();
        byCategory.forEach((category, acc) -> categories.add(new CategoryResult(category, round(acc[0]),
                acc[0].signum() == 0 ? BigDecimal.ZERO.setScale(2) : round(acc[1].divide(acc[0], 6, RoundingMode.HALF_UP)))));

        BigDecimal roundedFinal = round(finalScore);
        return new Result(results, categories, roundedFinal, AccreditationClassification.of(roundedFinal),
                criteriaCount, scoredCount);
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
