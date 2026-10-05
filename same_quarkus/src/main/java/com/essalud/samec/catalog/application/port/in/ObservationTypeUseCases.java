package com.essalud.samec.catalog.application.port.in;

import java.util.List;

import com.essalud.samec.catalog.domain.model.ObservationType;
import com.essalud.samec.shared.application.port.in.CrudUseCase;

public interface ObservationTypeUseCases extends CrudUseCase<ObservationType, Long> {

    List<ObservationType> listActive();
}
