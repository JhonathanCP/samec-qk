package com.essalud.samec.catalog.application.port.in;

import java.util.List;

import com.essalud.samec.catalog.domain.model.Standard;
import com.essalud.samec.shared.application.port.in.CrudUseCase;

public interface StandardUseCases extends CrudUseCase<Standard, Long> {

    List<Standard> listByMacroProcess(Long macroProcessId);
}
