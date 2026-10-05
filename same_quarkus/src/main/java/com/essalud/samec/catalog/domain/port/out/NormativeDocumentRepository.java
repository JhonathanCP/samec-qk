package com.essalud.samec.catalog.domain.port.out;

import java.util.Optional;

import com.essalud.samec.catalog.domain.model.NormativeDocument;
import com.essalud.samec.shared.domain.port.CrudRepository;

public interface NormativeDocumentRepository extends CrudRepository<NormativeDocument, Long> {

    Optional<NormativeDocument> findByTitle(String title);
}
