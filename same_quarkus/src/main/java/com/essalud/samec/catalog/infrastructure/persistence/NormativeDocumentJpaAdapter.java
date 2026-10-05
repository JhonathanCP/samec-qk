package com.essalud.samec.catalog.infrastructure.persistence;

import java.util.Map;
import java.util.Optional;

import com.essalud.samec.catalog.domain.model.NormativeDocument;
import com.essalud.samec.catalog.domain.port.out.NormativeDocumentRepository;
import com.essalud.samec.shared.infrastructure.persistence.JpaCrudAdapter;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NormativeDocumentJpaAdapter extends JpaCrudAdapter<NormativeDocument, NormativeDocumentEntity, Long>
        implements NormativeDocumentRepository {

    public NormativeDocumentJpaAdapter() {
        super(NormativeDocumentEntity.class, NormativeDocumentEntity::new, "Documento normativo");
    }

    @Override
    protected Long idOf(NormativeDocument model) {
        return model.id();
    }

    @Override
    protected NormativeDocument toDomain(NormativeDocumentEntity e) {
        return new NormativeDocument(e.getId(), e.getTitle(), e.getUrl());
    }

    @Override
    protected void copyToEntity(NormativeDocument m, NormativeDocumentEntity e) {
        e.setTitle(m.title());
        e.setUrl(m.url());
    }

    @Override
    public Optional<NormativeDocument> findByTitle(String title) {
        return title == null ? Optional.empty() : first("where e.title = :title", Map.of("title", title));
    }
}
