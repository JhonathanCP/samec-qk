package com.essalud.samec.catalog.application.service;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.essalud.samec.catalog.application.port.in.LeaCatalogSeedUseCase;
import com.essalud.samec.catalog.domain.model.AuditSource;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.CriterionCondition;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.catalog.domain.model.InstitutionalProcess;
import com.essalud.samec.catalog.domain.model.MacroProcess;
import com.essalud.samec.catalog.domain.model.NormativeDocument;
import com.essalud.samec.catalog.domain.model.NormativeReference;
import com.essalud.samec.catalog.domain.model.ObservationType;
import com.essalud.samec.catalog.domain.model.Standard;
import com.essalud.samec.catalog.domain.port.out.CriterionRepository;
import com.essalud.samec.catalog.domain.port.out.InstitutionalProcessRepository;
import com.essalud.samec.catalog.domain.port.out.MacroProcessRepository;
import com.essalud.samec.catalog.domain.port.out.NormativeDocumentRepository;
import com.essalud.samec.catalog.domain.port.out.ObservationTypeRepository;
import com.essalud.samec.catalog.domain.port.out.StandardRepository;
import com.essalud.samec.shared.domain.DomainException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class LeaCatalogSeedService implements LeaCatalogSeedUseCase {

    @Inject MacroProcessRepository macroProcesses;
    @Inject StandardRepository standards;
    @Inject CriterionRepository criteria;
    @Inject NormativeDocumentRepository normativeDocuments;
    @Inject InstitutionalProcessRepository processes;
    @Inject ObservationTypeRepository observationTypes;

    @Override
    public Optional<SeedResult> loadIfEmpty(LeaCatalog catalog) {
        return macroProcesses.count() > 0 ? Optional.empty() : Optional.of(load(catalog));
    }

    @Override
    public SeedResult load(LeaCatalog catalog) {
        Loader loader = new Loader();
        int standardCount = 0;
        int criterionCount = 0;
        int conditionCount = 0;
        for (MacroProcessSeed m : catalog.macroProcesses()) {
            MacroProcess macro = macroProcesses.save(
                    new MacroProcess(null, m.code(), m.name(), m.category(), m.scoreWeight(), m.displayOrder()));
            for (StandardSeed s : m.standards()) {
                Standard standard = standards.save(new Standard(null, macro.id(), s.code(), s.statement()));
                standardCount++;
                for (CriterionSeed c : s.criteria()) {
                    criteria.save(loader.criterion(standard.id(), c));
                    criterionCount++;
                    conditionCount += c.conditions().size();
                }
            }
        }
        return new SeedResult(catalog.macroProcesses().size(), standardCount, criterionCount, conditionCount,
                loader.createdDocuments, loader.createdProcesses);
    }

    @Override
    public int loadObservationTypesIfEmpty(List<ObservationTypeSeed> types) {
        if (observationTypes.count() > 0) return 0;
        types.forEach(t -> observationTypes.save(new ObservationType(null, t.code(), t.name(), true)));
        return types.size();
    }

    /** Builds criteria, creating each normative document and institutional process only once. */
    private final class Loader {

        private final Map<String, Long> documentByTitle = new HashMap<>();
        private final Map<String, Long> processByKey = new HashMap<>();
        int createdDocuments;
        int createdProcesses;

        Criterion criterion(Long standardId, CriterionSeed c) {
            try {
                Set<FacilityCategory> categories = EnumSet.noneOf(FacilityCategory.class);
                c.categories().forEach(label -> categories.add(FacilityCategory.fromLabel(label)));
                List<CriterionCondition> conditions = c.conditions().stream().map(this::condition).toList();
                return new Criterion(null, standardId, c.code(), c.statement(), c.type(), categories,
                        c.scoreLevels() == null ? List.of() : c.scoreLevels(), conditions);
            } catch (DomainException e) {
                throw new DomainException("LEA, criterio " + c.code() + ": " + e.getMessage());
            }
        }

        private CriterionCondition condition(ConditionSeed c) {
            Map<Long, NormativeReference> references = new LinkedHashMap<>(); // one per document
            for (ReferenceSeed r : c.normativeReferences() == null ? List.<ReferenceSeed>of() : c.normativeReferences()) {
                Long documentId = document(r.title(), r.url());
                references.putIfAbsent(documentId, new NormativeReference(documentId, r.applicableExcerpt()));
            }
            List<AuditSource> sources = c.sources() == null ? List.of() : c.sources().stream()
                    .map(s -> new AuditSource(null, s.name(), s.referenceUrl(), s.availability(), s.appliesSystemWide(),
                            s.displayOrder()))
                    .toList();
            return new CriterionCondition(null, c.description(), c.scoreWeight(), c.technique(),
                    process(c.process(), c.subprocess()), c.facilityFunction(), c.responsibleRole(), c.sourceLogic(),
                    List.copyOf(references.values()), sources, c.displayOrder(), c.verificationGuide());
        }

        private Long document(String title, String url) {
            return documentByTitle.computeIfAbsent(title, t -> normativeDocuments.findByTitle(t)
                    .orElseGet(() -> {
                        createdDocuments++;
                        return normativeDocuments.save(new NormativeDocument(null, t, url));
                    }).id());
        }

        private Long process(String process, String subprocess) {
            if (process == null) return null;
            Long parent = processFor(null, process);
            return subprocess == null ? parent : processFor(parent, subprocess);
        }

        private Long processFor(Long parentId, String name) {
            return processByKey.computeIfAbsent(parentId + "|" + name, k -> processes.findByParentIdAndName(parentId, name)
                    .orElseGet(() -> {
                        createdProcesses++;
                        return processes.save(new InstitutionalProcess(null, parentId, name));
                    }).id());
        }
    }
}
