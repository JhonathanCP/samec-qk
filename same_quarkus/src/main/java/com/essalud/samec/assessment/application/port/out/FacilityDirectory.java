package com.essalud.samec.assessment.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Facilities (IPRESS) as the assessment module sees them; implemented over the organization module. */
public interface FacilityDirectory {

    record FacilityInfo(Long id, String code, String name, Long networkId) {
    }

    Optional<FacilityInfo> find(Long facilityId);

    Map<Long, FacilityInfo> findAll(Collection<Long> facilityIds);
}
