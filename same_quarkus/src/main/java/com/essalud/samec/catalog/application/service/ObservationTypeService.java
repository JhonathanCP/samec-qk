package com.essalud.samec.catalog.application.service;

import java.util.List;

import com.essalud.samec.catalog.application.port.in.ObservationTypeUseCases;
import com.essalud.samec.catalog.domain.model.ObservationType;
import com.essalud.samec.catalog.domain.port.out.ObservationTypeRepository;
import com.essalud.samec.shared.application.CrudService;
import com.essalud.samec.shared.domain.port.CrudRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ObservationTypeService extends CrudService<ObservationType, Long> implements ObservationTypeUseCases {

    @Inject
    ObservationTypeRepository observationTypes;

    @Override
    protected CrudRepository<ObservationType, Long> repository() {
        return observationTypes;
    }

    @Override
    protected ObservationType withId(ObservationType m, Long id) {
        return new ObservationType(id, m.code(), m.name(), m.active());
    }

    @Override
    public List<ObservationType> listActive() {
        return observationTypes.findActive();
    }
}
