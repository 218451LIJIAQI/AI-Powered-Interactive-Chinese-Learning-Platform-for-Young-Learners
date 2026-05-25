package com.example.aichinese.service;

import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.example.aichinese.service.storage.StorageProvider;
import com.example.aichinese.service.storage.StorageReadRequest;
import com.example.aichinese.service.storage.StorageWriteRequest;
import com.example.aichinese.service.storage.StoredObject;

/**
 * Local filesystem implementation of {@link StorageProvider}.
 */
@Component
public class LocalStorageProvider implements StorageProvider {

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
    public StoredFileDownload load(StorageReadRequest request) {
        return UploadStorageSupport.loadManagedFile(
                request.configuredDirectory(),
                request.uploadedFile(),
                request.notFoundMessage());
    }
}
