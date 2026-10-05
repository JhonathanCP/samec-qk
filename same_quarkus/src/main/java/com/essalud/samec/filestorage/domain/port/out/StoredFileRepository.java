package com.essalud.samec.filestorage.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.essalud.samec.filestorage.domain.model.FileContent;
import com.essalud.samec.filestorage.domain.model.StoredFile;
import com.essalud.samec.shared.domain.port.CrudRepository;

/**
 * Files kept in the database (metadata in stored_files, bytes in stored_file_contents).
 * A new file is created with {@link #store}, never with {@code save}: it needs its bytes.
 * {@code save} only updates metadata of an existing file; {@code deleteById} removes both rows.
 */
public interface StoredFileRepository extends CrudRepository<StoredFile, Long> {

    /** Stores a new file; size and SHA-256 of the metadata must match the content. */
    StoredFile store(StoredFile metadata, FileContent content);

    Optional<FileContent> findContent(Long storedFileId);

    List<StoredFile> findBySha256(String sha256);
}
