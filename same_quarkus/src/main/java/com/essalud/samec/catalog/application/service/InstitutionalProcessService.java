package com.essalud.samec.catalog.application.service;

import java.util.List;
import java.util.stream.Collectors;

import com.essalud.samec.catalog.application.port.in.InstitutionalProcessUseCases;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.InstitutionalProcess;
import com.essalud.samec.catalog.domain.port.out.CriterionRepository;
import com.essalud.samec.catalog.domain.port.out.InstitutionalProcessRepository;
import com.essalud.samec.shared.application.CrudService;
import com.essalud.samec.shared.domain.DomainException;
import com.essalud.samec.shared.domain.NaturalOrder;
import com.essalud.samec.shared.domain.port.CrudRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class InstitutionalProcessService extends CrudService<InstitutionalProcess, Long>
        implements InstitutionalProcessUseCases {

    @Inject
    InstitutionalProcessRepository processes;

    @Inject
    CriterionRepository criteria;

    @Override
    protected CrudRepository<InstitutionalProcess, Long> repository() {
        return processes;
    }

    @Override
    protected InstitutionalProcess withId(InstitutionalProcess m, Long id) {
        return new InstitutionalProcess(id, m.parentId(), m.name());
    }

    @Override
    protected void validate(InstitutionalProcess process) {
        if (process.parentId() != null && !processes.existsById(process.parentId())) {
            throw new DomainException("El proceso padre " + process.parentId() + " no existe");
        }
        if (process.id() != null && process.parentId() != null && !processes.findByParentId(process.id()).isEmpty()) {
            throw new DomainException("Un proceso con subprocesos no puede volverse subproceso de otro");
        }
    }

    /** It cannot be deleted while it has sub-processes or conditions in it; the message says which. */
    @Override
    public void delete(Long id) {
        InstitutionalProcess process = processes.getById(id);
        int children = processes.findByParentId(id).size();
        if (children > 0) {
            throw new DomainException("\"" + process.name() + "\" tiene " + children
                    + " subproceso(s): elimínelos o muévalos antes de eliminarlo.");
        }
        List<Criterion> users = criteria.findByInstitutionalProcessId(id);
        if (!users.isEmpty()) {
            throw new DomainException("\"" + process.name() + "\" lo usan condiciones de " + users.size()
                    + " criterio(s) (" + codes(users) + "): asígneles otro proceso antes de eliminarlo.");
        }
        super.delete(id);
    }

    static String codes(List<Criterion> list) {
        String codes = list.stream().map(Criterion::code).sorted(NaturalOrder.INSTANCE).limit(8)
                .collect(Collectors.joining(", "));
        return list.size() > 8 ? codes + "…" : codes;
    }

    @Override
    public List<InstitutionalProcess> listChildren(Long parentId) {
        return processes.findByParentId(parentId);
    }
}
