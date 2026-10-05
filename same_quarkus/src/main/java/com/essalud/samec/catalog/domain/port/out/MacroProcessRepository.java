package com.essalud.samec.catalog.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.essalud.samec.catalog.domain.model.MacroProcess;
import com.essalud.samec.shared.domain.port.CrudRepository;

public interface MacroProcessRepository extends CrudRepository<MacroProcess, Long> {

    Optional<MacroProcess> findByCode(String code);

    /** All of them in display order. */
    List<MacroProcess> findAllOrdered();
}
