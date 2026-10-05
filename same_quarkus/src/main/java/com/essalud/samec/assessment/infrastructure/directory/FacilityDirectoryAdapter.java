package com.essalud.samec.assessment.infrastructure.directory;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.essalud.samec.assessment.application.port.out.FacilityDirectory;
import com.essalud.samec.organizacion.Ipress;
import com.essalud.samec.organizacion.IpressRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FacilityDirectoryAdapter implements FacilityDirectory {

    @Inject
    IpressRepository ipress;

    @Override
    public Optional<FacilityInfo> find(Long facilityId) {
        return facilityId == null ? Optional.empty() : ipress.findByIdOptional(facilityId).map(FacilityDirectoryAdapter::info);
    }

    @Override
    public Map<Long, FacilityInfo> findAll(Collection<Long> facilityIds) {
        if (facilityIds == null || facilityIds.isEmpty()) return Map.of();
        return ipress.list("id in ?1", facilityIds).stream()
                .map(FacilityDirectoryAdapter::info)
                .collect(Collectors.toMap(FacilityInfo::id, Function.identity()));
    }

    private static FacilityInfo info(Ipress i) {
        return new FacilityInfo(i.getId(), i.getCodigo(), i.getNombre(), i.getRed() == null ? null : i.getRed().getId());
    }
}
