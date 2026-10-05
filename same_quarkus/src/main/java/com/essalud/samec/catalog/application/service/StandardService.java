package com.essalud.samec.catalog.application.service;

import java.util.List;

import com.essalud.samec.catalog.application.port.in.StandardUseCases;
import com.essalud.samec.catalog.domain.model.Standard;
import com.essalud.samec.catalog.domain.port.out.CriterionRepository;
import com.essalud.samec.catalog.domain.port.out.MacroProcessRepository;
import com.essalud.samec.catalog.domain.port.out.StandardRepository;
import com.essalud.samec.shared.application.CrudService;
import com.essalud.samec.shared.domain.DomainException;
import com.essalud.samec.shared.domain.port.CrudRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class StandardService extends CrudService<Standard, Long> implements StandardUseCases {

    @Inject
    StandardRepository standards;

    @Inject
    MacroProcessRepository macroProcesses;

    @Inject
    CriterionRepository criteria;

    @Override
    protected CrudRepository<Standard, Long> repository() {
        return standards;
    }

    @Override
    protected Standard withId(Standard m, Long id) {
        return new Standard(id, m.macroProcessId(), m.code(), m.statement());
    }

    @Override
    protected void validate(Standard standard) {
        if (!macroProcesses.existsById(standard.macroProcessId())) {
            throw new DomainException("El macroproceso " + standard.macroProcessId() + " no existe");
        }
    }

    /** Only an empty standard can be deleted; it says how many criteria it still has. */
    @Override
    public void delete(Long id) {
        Standard standard = standards.getById(id);
        int count = criteria.findByStandardId(id).size();
        if (count > 0) {
            throw new DomainException("El estándar " + standard.code() + " tiene " + count
                    + " criterio(s): elimínelos o muévalos a otro estándar antes de eliminarlo.");
        }
        super.delete(id);
    }

    @Override
    public List<Standard> listByMacroProcess(Long macroProcessId) {
        return standards.findByMacroProcessId(macroProcessId);
    }
}
