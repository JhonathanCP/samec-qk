package com.essalud.samec.catalog.application.service;

import java.util.List;

import com.essalud.samec.catalog.application.port.in.CriterionUseCases;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.CriterionCondition;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.catalog.domain.model.NormativeReference;
import com.essalud.samec.catalog.domain.port.out.CriterionRepository;
import com.essalud.samec.catalog.domain.port.out.InstitutionalProcessRepository;
import com.essalud.samec.catalog.domain.port.out.NormativeDocumentRepository;
import com.essalud.samec.catalog.domain.port.out.StandardRepository;
import com.essalud.samec.shared.application.CrudService;
import com.essalud.samec.shared.domain.DomainException;
import com.essalud.samec.shared.domain.port.CrudRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CriterionService extends CrudService<Criterion, Long> implements CriterionUseCases {

    @Inject
    CriterionRepository criteria;

    @Inject
    StandardRepository standards;

    @Inject
    InstitutionalProcessRepository processes;

    @Inject
    NormativeDocumentRepository normativeDocuments;

    @Override
    protected CrudRepository<Criterion, Long> repository() {
        return criteria;
    }

    @Override
    protected Criterion withId(Criterion m, Long id) {
        return new Criterion(id, m.standardId(), m.code(), m.statement(), m.type(), m.applicableCategories(),
                m.scoreLevels(), m.conditions());
    }

    /** Every reference must exist, and the condition ids sent on update must be of this criterion. */
    @Override
    protected void validate(Criterion criterion) {
        if (!standards.existsById(criterion.standardId())) {
            throw new DomainException("El estándar " + criterion.standardId() + " no existe");
        }
        if (criterion.applicableCategories().isEmpty()) {
            throw new DomainException("Indique al menos una categoría de IPRESS a la que aplica el criterio");
        }
        if (criterion.conditions().isEmpty()) {
            throw new DomainException("El criterio debe tener al menos una condición");
        }
        for (CriterionCondition condition : criterion.conditions()) {
            Long processId = condition.institutionalProcessId();
            if (processId != null && !processes.existsById(processId)) {
                throw new DomainException("El proceso institucional " + processId + " no existe");
            }
            for (NormativeReference reference : condition.normativeReferences()) {
                if (!normativeDocuments.existsById(reference.documentId())) {
                    throw new DomainException("El documento normativo " + reference.documentId() + " no existe");
                }
            }
            if (condition.id() != null) {
                boolean own = criterion.id() != null && criteria.findByConditionId(condition.id())
                        .map(owner -> owner.id().equals(criterion.id())).orElse(false);
                if (!own) throw new DomainException("La condición " + condition.id() + " no pertenece a este criterio");
            }
        }
    }

    @Override
    public List<Criterion> listByStandard(Long standardId) {
        return criteria.findByStandardId(standardId);
    }

    @Override
    public List<Criterion> listApplicableTo(FacilityCategory category) {
        return criteria.findApplicableTo(category);
    }
}
