package com.essalud.samec.filestorage.application.service;

import java.util.Collection;
import java.util.List;

import com.essalud.samec.filestorage.application.port.in.FileStorageUseCases;
import com.essalud.samec.filestorage.domain.model.FileContent;
import com.essalud.samec.filestorage.domain.model.StoredFile;
import com.essalud.samec.filestorage.domain.port.out.StoredFileRepository;
import com.essalud.samec.filestorage.domain.service.FileTypeDetector;
import com.essalud.samec.shared.error.RecursoNoEncontradoException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class FileStorageService implements FileStorageUseCases {

    @Inject
    StoredFileRepository files;

    @Override
    public StoredFile store(NewFile file) {
        String name = FileTypeDetector.cleanName(file.originalName());
        FileContent content = new FileContent(file.bytes());
        String contentType = FileTypeDetector.detect(name, content);
        return files.store(StoredFile.describe(name, contentType, content), content);
    }

    @Override
    public List<StoredFile> storeAll(List<NewFile> newFiles) {
        return newFiles.stream().map(this::store).toList();
    }

    @Override
    public List<StoredFile> describe(Collection<Long> storedFileIds) {
        return files.findAllById(storedFileIds);
    }

    @Override
    public FileDownload download(Long storedFileId) {
        StoredFile metadata = files.getById(storedFileId);
        FileContent content = files.findContent(storedFileId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Contenido del archivo", storedFileId));
        return new FileDownload(metadata, content);
    }

    @Override
    public void delete(Long storedFileId) {
        files.deleteById(storedFileId);
    }
}
