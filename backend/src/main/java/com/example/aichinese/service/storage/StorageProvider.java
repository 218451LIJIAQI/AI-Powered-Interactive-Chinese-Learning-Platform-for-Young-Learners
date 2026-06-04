package com.example.aichinese.service.storage;

import org.springframework.web.multipart.MultipartFile;

import com.example.aichinese.service.StoredFileDownload;

public interface StorageProvider {

    StoredObject store(MultipartFile multipartFile, StorageWriteRequest request);

    void deleteQuietly(StoredObject storedObject);

    StoredFileDownload load(StorageReadRequest request);
}
