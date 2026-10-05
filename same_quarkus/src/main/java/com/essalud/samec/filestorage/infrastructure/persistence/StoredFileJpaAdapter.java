package com.essalud.samec.filestorage.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.essalud.samec.filestorage.domain.model.FileContent;
import com.essalud.samec.filestorage.domain.model.StoredFile;
import com.essalud.samec.filestorage.domain.port.out.StoredFileRepository;
import com.essalud.samec.shared.infrastructure.persistence.JpaCrudAdapter;

import jakarta.enterprise.context.ApplicationScoped;

/** Files as BLOB: metadata in stored_files, bytes in stored_file_contents (loaded only on demand). */
@ApplicationScoped
public class StoredFileJpaAdapter extends JpaCrudAdapter<StoredFile, StoredFileEntity, Long>
        implements StoredFileRepository {

    public StoredFileJpaAdapter() {
        super(StoredFileEntity.class, StoredFileEntity::new, "Archivo");
    }

    @Override
    protected Long idOf(StoredFile model) {
        return model.id();
    }

    @Override
    protected StoredFile toDomain(StoredFileEntity e) {
        return new StoredFile(e.getId(), e.getOriginalName(), e.getContentType(), e.getSizeBytes(), e.getSha256(),
                e.getCreatedBy(), e.getCreatedAt());
    }

    @Override
    protected void copyToEntity(StoredFile m, StoredFileEntity e) {
        e.setOriginalName(m.originalName());
        e.setContentType(m.contentType());
        e.setSizeBytes(m.sizeBytes());
        e.setSha256(m.sha256());
    }

    /** Only updates metadata; a new file needs its bytes, see {@link #store}. */
    @Override
    public StoredFile save(StoredFile model) {
        if (model.id() == null) throw new IllegalStateException("A new file is created with store(metadata, content)");
        return super.save(model);
    }

    @Override
    public StoredFile store(StoredFile metadata, FileContent content) {
        if (metadata.id() != null) throw new IllegalArgumentException("The file is already stored: " + metadata.id());
        if (!metadata.matches(content)) throw new IllegalArgumentException("Size or SHA-256 do not match the content");
        StoredFileEntity file = new StoredFileEntity();
        copyToEntity(metadata, file);
        em.persist(file);
        StoredFileContentEntity bytes = new StoredFileContentEntity();
        bytes.setStoredFile(file);
        bytes.setContent(content.bytes());
        em.persist(bytes);
        em.flush();
        return toDomain(file);
    }

    @Override
    public Optional<FileContent> findContent(Long storedFileId) {
        if (storedFileId == null) return Optional.empty();
        return Optional.ofNullable(em.find(StoredFileContentEntity.class, storedFileId))
                .map(c -> new FileContent(c.getContent()));
    }

    @Override
    public List<StoredFile> findBySha256(String sha256) {
        if (sha256 == null) return List.of();
        return list("where e.sha256 = :sha order by e.id", Map.of("sha", sha256));
    }

    /** Removes the bytes (the BLOB is lazy, so it is not loaded) and then the metadata. */
    @Override
    public void deleteById(Long id) {
        findEntity(id);
        StoredFileContentEntity content = em.find(StoredFileContentEntity.class, id);
        if (content != null) em.remove(content);
        super.deleteById(id);
    }
}
