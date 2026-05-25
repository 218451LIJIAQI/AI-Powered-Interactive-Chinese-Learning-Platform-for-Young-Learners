package com.example.aichinese.service.storage;

import org.springframework.web.multipart.MultipartFile;

import com.example.aichinese.service.StoredFileDownload;

/**
 * Pluggable binary-object storage boundary for uploaded learner and teacher
 * artefacts.
 *
 * <p>The default implementation writes to the local filesystem, but services
 * depend on this interface so production can swap in S3, OSS, or another object
 * store without touching upload validation, metadata persistence, or access
 * control logic.</p>
 */
public interface StorageProvider {

    StoredObject store(MultipartFile multipartFile, StorageWriteRequest request);

    StoredFileDownload load(StorageReadRequest request);
}
