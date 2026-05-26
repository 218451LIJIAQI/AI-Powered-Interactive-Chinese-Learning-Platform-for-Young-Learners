package com.example.aichinese.service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.example.aichinese.service.storage.StorageProvider;
import com.example.aichinese.service.storage.StorageReadRequest;
import com.example.aichinese.service.storage.StorageWriteRequest;
import com.example.aichinese.service.storage.StoredObject;
import com.example.aichinese.util.ExceptionLogUtils;

/**
 * Local filesystem implementation of {@link StorageProvider}.
 */
@Component
public class LocalStorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageProvider.class);

    @Override
    public StoredObject store(MultipartFile multipartFile, StorageWriteRequest request) {
        Objects.requireNonNull(multipartFile, "multipartFile");
        return new StoredObject(UploadStorageSupport.storeMultipartFile(
                multipartFile,
                request.baseDirectory(),
                request.storedFileName(),
                request.invalidTargetMessage(),
                request.storageFailureMessage()));
    }

    @Override
    public void deleteQuietly(StoredObject storedObject) {
        if (storedObject == null) {
            return;
        }
        try {
            Files.deleteIfExists(storedObject.location());
        } catch (IOException exception) {
            log.warn("Failed to delete stored object during upload rollback for name {}: {}",
                    safeFileName(storedObject),
                    ExceptionLogUtils.exceptionType(exception));
            log.debug("Stored object cleanup stack trace for name {}.", safeFileName(storedObject), exception);
        }
    }

    @Override
    public StoredFileDownload load(StorageReadRequest request) {
        return UploadStorageSupport.loadManagedFile(
                request.configuredDirectory(),
                request.uploadedFile(),
                request.notFoundMessage());
    }

    private static String safeFileName(StoredObject storedObject) {
        if (storedObject == null || storedObject.location() == null || storedObject.location().getFileName() == null) {
            return "-";
        }
        return storedObject.location().getFileName().toString();
    }
}
