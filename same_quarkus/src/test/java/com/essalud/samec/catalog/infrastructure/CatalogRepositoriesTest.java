package com.essalud.samec.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.essalud.samec.catalog.domain.model.AuditSource;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.CriterionCondition;
import com.essalud.samec.catalog.domain.model.CriterionType;
import com.essalud.samec.catalog.domain.model.EvaluationTechnique;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.catalog.domain.model.InstitutionalProcess;
import com.essalud.samec.catalog.domain.model.MacroProcess;
import com.essalud.samec.catalog.domain.model.MacroProcessCategory;
import com.essalud.samec.catalog.domain.model.NormativeDocument;
import com.essalud.samec.catalog.domain.model.NormativeReference;
import com.essalud.samec.catalog.domain.model.ObservationType;
import com.essalud.samec.catalog.domain.model.ScoreLevel;
import com.essalud.samec.catalog.domain.model.SourceAvailability;
import com.essalud.samec.catalog.domain.model.SourceLogic;
import com.essalud.samec.catalog.domain.model.Standard;
import com.essalud.samec.catalog.domain.port.out.CriterionRepository;
import com.essalud.samec.catalog.domain.port.out.InstitutionalProcessRepository;
import com.essalud.samec.catalog.domain.port.out.MacroProcessRepository;
import com.essalud.samec.catalog.domain.port.out.NormativeDocumentRepository;
import com.essalud.samec.catalog.domain.port.out.ObservationTypeRepository;
import com.essalud.samec.catalog.domain.port.out.StandardRepository;
import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;
import com.essalud.samec.shared.error.RecursoNoEncontradoException;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** CRUD of every catalog table through the repository ports (each test rolls back). */
@QuarkusTest
class CatalogRepositoriesTest {

    @Inject MacroProcessRepository macroProcesses;
    @Inject StandardRepository standards;
    @Inject InstitutionalProcessRepository processes;
    @Inject NormativeDocumentRepository normativeDocuments;
    @Inject ObservationTypeRepository observationTypes;
    @Inject CriterionRepository criteria;

    private MacroProcess macro(String code, int order) {
        return macroProcesses.save(new MacroProcess(null, code, "Macro " + code, MacroProcessCategory.MANAGEMENT, BigDecimal.TEN, order));
    }

    @Test
    @TestTransaction
    void genericCrudOperations() {
        long before = macroProcesses.count();
        MacroProcess dir = macro("T-DIR", 2);
        MacroProcess ger = macro("T-GER", 1);
        MacroProcess pre = macro("T-PRE", 3);
        assertThat(dir.id()).isNotNull();

        // read
        assertThat(macroProcesses.findById(dir.id())).contains(dir);
        assertThat(macroProcesses.getById(dir.id()).code()).isEqualTo("T-DIR");
        assertThat(macroProcesses.findById(-1L)).isEmpty();
        assertThatThrownBy(() -> macroProcesses.getById(-1L)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(macroProcesses.existsById(dir.id())).isTrue();
        assertThat(macroProcesses.count()).isEqualTo(before + 3);
        assertThat(macroProcesses.findAllById(List.of(ger.id(), pre.id()))).extracting(MacroProcess::code)
                .containsExactly("T-GER", "T-PRE");
        assertThat(macroProcesses.findAll()).extracting(MacroProcess::code).contains("T-DIR", "T-GER", "T-PRE");

        // paging
        Page<MacroProcess> page = macroProcesses.findAll(PageRequest.of(0, 2));
        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(before + 3);
        assertThat(page.totalPages()).isEqualTo((int) ((before + 3 + 1) / 2));

        // update
        MacroProcess renamed = macroProcesses.save(new MacroProcess(dir.id(), "T-DIR", "Direccionamiento",
                MacroProcessCategory.MANAGEMENT, BigDecimal.valueOf(8), 2));
        assertThat(renamed.id()).isEqualTo(dir.id());
        assertThat(macroProcesses.findByCode("T-DIR")).map(MacroProcess::name).contains("Direccionamiento");
        assertThatThrownBy(() -> macroProcesses.save(new MacroProcess(-1L, "X", "X", MacroProcessCategory.SUPPORT, BigDecimal.ONE, 9)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        // custom query
        assertThat(macroProcesses.findAllOrdered()).extracting(MacroProcess::code)
                .containsSubsequence("T-GER", "T-DIR", "T-PRE");

        // delete
        macroProcesses.deleteById(pre.id());
        assertThat(macroProcesses.existsById(pre.id())).isFalse();
        assertThatThrownBy(() -> macroProcesses.deleteById(pre.id())).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @TestTransaction
    void standardsBelongToMacroProcesses() {
        MacroProcess macro = macro("T-STD", 1);
        Standard a = standards.save(new Standard(null, macro.id(), "T-STD-2", "Segundo"));
        standards.save(new Standard(null, macro.id(), "T-STD-1", "Primero"));

        assertThat(standards.findByMacroProcessId(macro.id())).extracting(Standard::code).containsExactly("T-STD-1", "T-STD-2");
        standards.save(new Standard(a.id(), macro.id(), "T-STD-2", "Segundo (editado)"));
        assertThat(standards.findByCode("T-STD-2")).map(Standard::statement).contains("Segundo (editado)");
        standards.deleteById(a.id());
        assertThat(standards.findByMacroProcessId(macro.id())).hasSize(1);
    }

    @Test
    @TestTransaction
    void institutionalProcessesFormATree() {
        InstitutionalProcess root = processes.save(new InstitutionalProcess(null, null, "T-Gestión de la calidad"));
        processes.save(new InstitutionalProcess(null, root.id(), "Acreditación"));
        processes.save(new InstitutionalProcess(null, root.id(), "Auditoría"));

        assertThat(processes.findRoots()).extracting(InstitutionalProcess::name).contains("T-Gestión de la calidad");
        assertThat(processes.findByParentId(root.id())).extracting(InstitutionalProcess::name)
                .containsExactly("Acreditación", "Auditoría");
        assertThat(processes.findByParentIdAndName(root.id(), "Auditoría")).isPresent();
        assertThat(processes.findByParentIdAndName(null, "T-Gestión de la calidad")).contains(root);
        assertThatThrownBy(() -> processes.save(new InstitutionalProcess(root.id(), root.id(), "Ciclo")))
                .hasMessageContaining("propio padre");
    }

    @Test
    @TestTransaction
    void normativeDocumentsAndObservationTypes() {
        NormativeDocument nts = normativeDocuments.save(new NormativeDocument(null, "T-NTS 050", "https://gob.pe/nts"));
        assertThat(normativeDocuments.findByTitle("T-NTS 050")).contains(nts);
        normativeDocuments.deleteById(nts.id());
        assertThat(normativeDocuments.findByTitle("T-NTS 050")).isEmpty();

        ObservationType wrongYear = observationTypes.save(new ObservationType(null, "T-WRONG_YEAR", "Otro año", true));
        observationTypes.save(new ObservationType(null, "T-OLD", "Obsoleto", false));
        assertThat(observationTypes.findActive()).extracting(ObservationType::code)
                .contains("T-WRONG_YEAR").doesNotContain("T-OLD");
        observationTypes.save(new ObservationType(wrongYear.id(), "T-WRONG_YEAR", "Otro año", false));
        assertThat(observationTypes.findActive()).extracting(ObservationType::code).doesNotContain("T-WRONG_YEAR");
        assertThat(observationTypes.findByCode("T-OLD")).isPresent();
    }

    @Test
    @TestTransaction
    void criterionIsSavedWithAllItsChildren() {
        MacroProcess macro = macro("T-CRI", 1);
        Standard standard = standards.save(new Standard(null, macro.id(), "T-CRI-1", "Estándar"));
        InstitutionalProcess process = processes.save(new InstitutionalProcess(null, null, "T-Planeamiento"));
        NormativeDocument nts = normativeDocuments.save(new NormativeDocument(null, "T-NTS crit", null));
        NormativeDocument rm = normativeDocuments.save(new NormativeDocument(null, "T-RM 456", null));

        CriterionCondition documentary = new CriterionCondition(null, "Plan aprobado", 1, EvaluationTechnique.DOCUMENT_REVIEW,
                process.id(), "Elaborar el plan", "Jefe de calidad", SourceLogic.ANY,
                List.of(new NormativeReference(nts.id(), "Numeral 5"), new NormativeReference(rm.id(), null)),
                List.of(new AuditSource(null, "Plan anual", null, SourceAvailability.PROVIDED_BY_FACILITY, false, 1),
                        new AuditSource(null, "Directiva", "https://essalud/dir.pdf", SourceAvailability.PREDEFINED, true, 2)),
                1);
        CriterionCondition interview = new CriterionCondition(null, "El personal lo conoce", 1, EvaluationTechnique.INTERVIEW,
                null, null, null, null, List.of(), List.of(), 2);
        Criterion created = criteria.save(new Criterion(null, standard.id(), "T-CRI-1-1", "Enunciado", CriterionType.PROCESS,
                Set.of(FacilityCategory.I_3, FacilityCategory.II_1),
                List.of(new ScoreLevel(0, "No cumple"), new ScoreLevel(1, "Parcial"), new ScoreLevel(2, "Cumple")),
                List.of(documentary, interview)));

        Criterion loaded = criteria.getById(created.id());
        assertThat(loaded.scoreLevels()).extracting(ScoreLevel::score).containsExactly(0, 1, 2);
        assertThat(loaded.applicableCategories()).containsExactlyInAnyOrder(FacilityCategory.I_3, FacilityCategory.II_1);
        assertThat(loaded.conditions()).extracting(CriterionCondition::description)
                .containsExactly("Plan aprobado", "El personal lo conoce");
        CriterionCondition loadedDoc = loaded.conditions().get(0);
        assertThat(loadedDoc.institutionalProcessId()).isEqualTo(process.id());
        assertThat(loadedDoc.normativeReferences()).extracting(NormativeReference::documentId)
                .containsExactlyInAnyOrder(nts.id(), rm.id());
        assertThat(loadedDoc.sources()).extracting(AuditSource::name).containsExactly("Plan anual", "Directiva");
        Long sourceId = loadedDoc.sources().get(0).id();

        // Lookups used by the assessment
        assertThat(criteria.findByAuditSourceId(sourceId)).map(Criterion::id).contains(created.id());
        assertThat(criteria.findByConditionId(loadedDoc.id())).map(Criterion::id).contains(created.id());
        assertThat(criteria.findApplicableTo(FacilityCategory.II_1)).extracting(Criterion::code).contains("T-CRI-1-1");
        assertThat(criteria.findApplicableTo(FacilityCategory.III_1)).extracting(Criterion::code).doesNotContain("T-CRI-1-1");
        assertThat(criteria.findByStandardId(standard.id())).hasSize(1);

        // Update: edit condition 1 (drop a source and a reference), replace condition 2 by a new one
        // with the same display order, change categories and a level description.
        CriterionCondition editedDoc = new CriterionCondition(loadedDoc.id(), "Plan aprobado y difundido", 1,
                EvaluationTechnique.DOCUMENT_REVIEW, process.id(), "Elaborar y difundir", "Jefe de calidad", null,
                List.of(new NormativeReference(nts.id(), "Numeral 5.1")),
                List.of(new AuditSource(sourceId, "Plan anual aprobado", null, SourceAvailability.PROVIDED_BY_FACILITY, false, 1)),
                1);
        CriterionCondition observation = new CriterionCondition(null, "Se observa el plan publicado", 1,
                EvaluationTechnique.OBSERVATION, null, null, null, null, List.of(), List.of(), 2);
        criteria.save(new Criterion(created.id(), standard.id(), "T-CRI-1-1", "Enunciado", CriterionType.PROCESS,
                Set.of(FacilityCategory.III_1),
                List.of(new ScoreLevel(0, "No cumple"), new ScoreLevel(1, "Cumple parcialmente"), new ScoreLevel(2, "Cumple")),
                List.of(editedDoc, observation)));

        Criterion updated = criteria.getById(created.id());
        assertThat(updated.applicableCategories()).containsExactly(FacilityCategory.III_1);
        assertThat(updated.scoreLevels().get(1).description()).isEqualTo("Cumple parcialmente");
        assertThat(updated.conditions()).extracting(CriterionCondition::technique)
                .containsExactly(EvaluationTechnique.DOCUMENT_REVIEW, EvaluationTechnique.OBSERVATION);
        assertThat(updated.conditions().get(0).id()).isEqualTo(loadedDoc.id());
        assertThat(updated.conditions().get(0).sources()).singleElement()
                .satisfies(s -> {
                    assertThat(s.id()).isEqualTo(sourceId);
                    assertThat(s.name()).isEqualTo("Plan anual aprobado");
                });
        assertThat(updated.conditions().get(0).normativeReferences()).singleElement()
                .extracting(NormativeReference::applicableExcerpt).isEqualTo("Numeral 5.1");

        // Delete removes the whole aggregate
        criteria.deleteById(created.id());
        assertThat(criteria.findByCode("T-CRI-1-1")).isEmpty();
        assertThat(criteria.findByAuditSourceId(sourceId)).isEmpty();
    }
}
