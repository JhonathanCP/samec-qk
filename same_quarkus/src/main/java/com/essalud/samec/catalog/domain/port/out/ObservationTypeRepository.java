package com.essalud.samec.catalog.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.essalud.samec.catalog.domain.model.ObservationType;
import com.essalud.samec.shared.domain.port.CrudRepository;

public interface ObservationTypeRepository extends CrudRepository<ObservationType, Long> {

    Optional<ObservationType> findByCode(String code);

    List<ObservationType> findActive();
}
