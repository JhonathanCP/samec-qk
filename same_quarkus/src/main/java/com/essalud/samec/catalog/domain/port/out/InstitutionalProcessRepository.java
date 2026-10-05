package com.essalud.samec.catalog.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.essalud.samec.catalog.domain.model.InstitutionalProcess;
import com.essalud.samec.shared.domain.port.CrudRepository;

public interface InstitutionalProcessRepository extends CrudRepository<InstitutionalProcess, Long> {

    /** Top-level processes (no parent). */
    List<InstitutionalProcess> findRoots();

    List<InstitutionalProcess> findByParentId(Long parentId);

    /** By name under a parent; a null parent looks among the top-level processes. */
    Optional<InstitutionalProcess> findByParentIdAndName(Long parentId, String name);
}
