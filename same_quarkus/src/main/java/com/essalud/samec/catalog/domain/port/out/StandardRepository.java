package com.essalud.samec.catalog.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.essalud.samec.catalog.domain.model.Standard;
import com.essalud.samec.shared.domain.port.CrudRepository;

public interface StandardRepository extends CrudRepository<Standard, Long> {

    Optional<Standard> findByCode(String code);

    List<Standard> findByMacroProcessId(Long macroProcessId);
}
