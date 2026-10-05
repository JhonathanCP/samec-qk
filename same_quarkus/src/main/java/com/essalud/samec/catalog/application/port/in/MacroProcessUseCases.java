package com.essalud.samec.catalog.application.port.in;

import com.essalud.samec.catalog.domain.model.MacroProcess;
import com.essalud.samec.shared.application.port.in.CrudUseCase;

/** Macro processes of the LEA; {@code list()} returns them in display order. */
public interface MacroProcessUseCases extends CrudUseCase<MacroProcess, Long> {
}
