package com.essalud.samec.filestorage.application.port.in;

import java.util.Collection;
import java.util.List;

import com.essalud.samec.filestorage.domain.model.FileContent;
import com.essalud.samec.filestorage.domain.model.StoredFile;

/**
 * Keeps files in the database. Other modules use it to store what users upload; who may download
 * a file is decided by the module that owns it.
 */
public interface FileStorageUseCases {

    /** A file as uploaded: name and bytes (the type is detected from them). */
    record NewFile(String originalName, byte[] bytes) {
    }

    record FileDownload(StoredFile metadata, FileContent content) {
    }

    StoredFile store(NewFile file);

    List<StoredFile> storeAll(List<NewFile> files);

    List<StoredFile> describe(Collection<Long> storedFileIds);

    FileDownload download(Long storedFileId);

    void delete(Long storedFileId);
}
