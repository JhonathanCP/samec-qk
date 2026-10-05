package com.essalud.samec.catalog.application.service;

import java.util.List;

import com.essalud.samec.catalog.application.port.in.NormativeDocumentUseCases;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.NormativeDocument;
import com.essalud.samec.catalog.domain.port.out.CriterionRepository;
import com.essalud.samec.catalog.domain.port.out.NormativeDocumentRepository;
import com.essalud.samec.shared.application.CrudService;
import com.essalud.samec.shared.domain.DomainException;
import com.essalud.samec.shared.domain.port.CrudRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class NormativeDocumentService extends CrudService<NormativeDocument, Long> implements NormativeDocumentUseCases {

    @Inject
    NormativeDocumentRepository documents;

    @Inject
    CriterionRepository criteria;

    @Override
    protected CrudRepository<NormativeDocument, Long> repository() {
        return documents;
    }

    @Override
    protected NormativeDocument withId(NormativeDocument m, Long id) {
        return new NormativeDocument(id, m.title(), m.url());
    }

    /** A document cited by some condition cannot be deleted; the message says by which criteria. */
    @Override
    public void delete(Long id) {
        documents.getById(id);
        List<Criterion> citing = criteria.findByNormativeDocumentId(id);
        if (!citing.isEmpty()) {
            throw new DomainException("La norma está citada en " + citing.size() + " criterio(s) ("
                    + InstitutionalProcessService.codes(citing) + "): quítela de esas condiciones antes de eliminarla.");
        }
        super.delete(id);
    }
}
