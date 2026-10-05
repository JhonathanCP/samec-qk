package com.essalud.samec.catalog.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.essalud.samec.catalog.application.port.in.CatalogTreeUseCase;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.CriterionCondition;
import com.essalud.samec.catalog.domain.model.Standard;
import com.essalud.samec.catalog.domain.port.out.CriterionRepository;
import com.essalud.samec.catalog.domain.port.out.MacroProcessRepository;
import com.essalud.samec.catalog.domain.port.out.StandardRepository;
import com.essalud.samec.shared.domain.NaturalOrder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class CatalogTreeService implements CatalogTreeUseCase {

    @Inject MacroProcessRepository macroProcesses;
    @Inject StandardRepository standards;
    @Inject CriterionRepository criteria;

    @Override
    public List<MacroProcessNode> tree() {
        Map<Long, List<Criterion>> criteriaByStandard = criteria.findAll().stream()
                .collect(Collectors.groupingBy(Criterion::standardId));
        Map<Long, List<Standard>> standardsByMacro = standards.findAll().stream()
                .collect(Collectors.groupingBy(Standard::macroProcessId));
        return macroProcesses.findAllOrdered().stream()
                .map(m -> new MacroProcessNode(m.id(), m.code(), m.name(), m.category(), m.scoreWeight(), m.displayOrder(),
                        standardsByMacro.getOrDefault(m.id(), List.of()).stream()
                                .sorted(Comparator.comparing(Standard::code, NaturalOrder.INSTANCE))
                                .map(s -> new StandardNode(s.id(), s.code(), s.statement(),
                                        criteriaByStandard.getOrDefault(s.id(), List.of()).stream()
                                                .sorted(Comparator.comparing(Criterion::code, NaturalOrder.INSTANCE))
                                                .map(CatalogTreeService::summary)
                                                .toList()))
                                .toList()))
                .toList();
    }

    @Override
    public List<Usage> usagesOfInstitutionalProcess(Long institutionalProcessId) {
        return usages(criteria.findByInstitutionalProcessId(institutionalProcessId),
                c -> institutionalProcessId.equals(c.institutionalProcessId()));
    }

    @Override
    public List<Usage> usagesOfNormativeDocument(Long normativeDocumentId) {
        return usages(criteria.findByNormativeDocumentId(normativeDocumentId),
                c -> c.normativeReferences().stream().anyMatch(r -> normativeDocumentId.equals(r.documentId())));
    }

    private static List<Usage> usages(List<Criterion> found, Predicate<CriterionCondition> uses) {
        return found.stream()
                .sorted(Comparator.comparing(Criterion::code, NaturalOrder.INSTANCE))
                .flatMap(cr -> cr.conditions().stream().filter(uses)
                        .map(c -> new Usage(cr.id(), cr.code(), cr.standardId(), c.id(), c.description())))
                .toList();
    }

    private static CriterionSummary summary(Criterion c) {
        int sourcesToUpload = 0;
        int onSite = 0;
        int weight = 0;
        for (CriterionCondition condition : c.conditions()) {
            weight += condition.scoreWeight();
            if (!condition.technique().isEvidenceBased()) onSite++;
            sourcesToUpload += condition.sourcesRequiringEvidence().size();
        }
        return new CriterionSummary(c.id(), c.code(), c.statement(), c.type(), c.applicableCategories(),
                c.conditions().size(), sourcesToUpload, onSite, weight);
    }
}
