package com.essalud.samec.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.essalud.samec.catalog.application.port.in.LeaCatalogSeedUseCase;
import com.essalud.samec.catalog.application.port.in.LeaCatalogSeedUseCase.SeedResult;
import com.essalud.samec.catalog.domain.model.AuditSource;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.CriterionCondition;
import com.essalud.samec.catalog.domain.model.CriterionType;
import com.essalud.samec.catalog.domain.model.EvaluationTechnique;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.catalog.domain.model.MacroProcess;
import com.essalud.samec.catalog.domain.model.MacroProcessCategory;
import com.essalud.samec.catalog.domain.model.SourceAvailability;
import com.essalud.samec.catalog.domain.model.SourceLogic;
import com.essalud.samec.catalog.domain.port.out.CriterionRepository;
import com.essalud.samec.catalog.domain.port.out.MacroProcessRepository;
import com.essalud.samec.catalog.infrastructure.seed.LeaCatalogInitializer;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** The real LEA (seed/lea.json) loads through the domain rules (rolled back afterwards). */
@QuarkusTest
class LeaCatalogSeedTest {

    @Inject LeaCatalogInitializer initializer;
    @Inject LeaCatalogSeedUseCase seeder;
    @Inject MacroProcessRepository macroProcesses;
    @Inject CriterionRepository criteria;

    @Test
    @TestTransaction
    void loadsTheWholeManual() {
        SeedResult result = seeder.load(initializer.read());

        assertThat(result.macroProcesses()).isEqualTo(22);
        assertThat(result.standards()).isEqualTo(71);
        assertThat(result.criteria()).isEqualTo(337);
        assertThat(result.conditions()).isEqualTo(539);
        assertThat(result.normativeDocuments()).isPositive();
        assertThat(result.institutionalProcesses()).isPositive();

        // Weights of the scoring model: GER 35 + PRE 40 + APO 25 = 100
        List<MacroProcess> lea = macroProcesses.findAllOrdered().stream()
                .filter(m -> Set.of("DIR", "GRH", "GCA", "MRA", "GSD", "CGP", "ATA", "AEX", "ATH", "EMG", "ATQ", "DIV",
                        "ADT", "ADA", "RCR", "GMD", "GIN", "DLDE", "MRS", "NYD", "GIM", "EIF").contains(m.code()))
                .toList();
        assertThat(lea).hasSize(22);
        assertThat(lea.stream().map(MacroProcess::scoreWeight).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100");
        assertThat(lea.stream().filter(m -> m.category() == MacroProcessCategory.MANAGEMENT)
                .map(MacroProcess::scoreWeight).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("35");

        // DIR1-1: applies to every category; 0/1/2 levels split from the statement; predefined PEI
        Criterion dir11 = criteria.findByCode("DIR1-1").orElseThrow();
        assertThat(dir11.type()).isEqualTo(CriterionType.STRUCTURE);
        assertThat(dir11.applicableCategories()).hasSize(7);
        assertThat(dir11.statement()).doesNotContain("0:");
        assertThat(dir11.scoreLevels()).extracting(l -> l.score()).containsExactly(0, 1, 2);
        AuditSource pei = dir11.conditions().get(0).sources().get(0);
        assertThat(pei.availability()).isEqualTo(SourceAvailability.PREDEFINED);
        assertThat(pei.referenceUrl()).startsWith("https://");
        assertThat(dir11.conditions().get(0).normativeReferences()).hasSize(1);
        assertThat(dir11.conditions().get(0).institutionalProcessId()).isNotNull();

        // DIR1-2 does not apply to I-1
        assertThat(criteria.findByCode("DIR1-2").orElseThrow().appliesTo(FacilityCategory.I_1)).isFalse();

        // DIR1-3: several predefined sources, all required (Y)
        CriterionCondition dir13 = criteria.findByCode("DIR1-3").orElseThrow().conditions().get(0);
        assertThat(dir13.sources()).hasSizeGreaterThan(1);
        assertThat(dir13.sourceLogic()).isEqualTo(SourceLogic.ALL);

        // GRH3-1: the observation condition says what to check, and has nothing to upload
        CriterionCondition observation = criteria.findByCode("GRH3-1").orElseThrow().conditions().stream()
                .filter(c -> c.technique() == EvaluationTechnique.OBSERVATION).findFirst().orElseThrow();
        assertThat(observation.sources()).isEmpty();
        assertThat(observation.verificationGuide()).contains("Higiene de manos");

        // Some sources are not implemented yet in EsSalud
        assertThat(criteria.findApplicableTo(FacilityCategory.III_1).stream()
                .flatMap(c -> c.conditions().stream()).flatMap(c -> c.sources().stream())
                .filter(s -> s.availability() == SourceAvailability.NOT_IMPLEMENTED)).isNotEmpty();

        // Already loaded: loadIfEmpty does nothing
        assertThat(seeder.loadIfEmpty(initializer.read())).isEmpty();
    }
}
