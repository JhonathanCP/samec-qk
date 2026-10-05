package com.essalud.samec.catalog.application.port.in;

import java.util.List;

import com.essalud.samec.catalog.domain.model.InstitutionalProcess;
import com.essalud.samec.shared.application.port.in.CrudUseCase;

public interface InstitutionalProcessUseCases extends CrudUseCase<InstitutionalProcess, Long> {

    /** Sub-processes of a process; a null parent lists the top-level processes. */
    List<InstitutionalProcess> listChildren(Long parentId);
}
