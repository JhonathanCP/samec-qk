package com.essalud.samec.catalog.application.service;

import java.util.List;

import com.essalud.samec.catalog.application.port.in.MacroProcessUseCases;
import com.essalud.samec.catalog.domain.model.MacroProcess;
import com.essalud.samec.catalog.domain.port.out.MacroProcessRepository;
import com.essalud.samec.catalog.domain.port.out.StandardRepository;
import com.essalud.samec.shared.application.CrudService;
import com.essalud.samec.shared.domain.DomainException;
import com.essalud.samec.shared.domain.port.CrudRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MacroProcessService extends CrudService<MacroProcess, Long> implements MacroProcessUseCases {

    @Inject
    MacroProcessRepository macroProcesses;

    @Inject
    StandardRepository standards;

    @Override
    protected CrudRepository<MacroProcess, Long> repository() {
        return macroProcesses;
    }

    @Override
    protected MacroProcess withId(MacroProcess m, Long id) {
        return new MacroProcess(id, m.code(), m.name(), m.category(), m.scoreWeight(), m.displayOrder());
    }

    @Override
    public List<MacroProcess> list() {
        return macroProcesses.findAllOrdered();
    }

    /** Only an empty macro process can be deleted; it says how many standards it still has. */
    @Override
    public void delete(Long id) {
        MacroProcess macro = macroProcesses.getById(id);
        int count = standards.findByMacroProcessId(id).size();
        if (count > 0) {
            throw new DomainException("El macroproceso " + macro.code() + " tiene " + count
                    + " estándar(es): elimínelos o muévalos a otro macroproceso antes de eliminarlo.");
        }
        super.delete(id);
    }
}
