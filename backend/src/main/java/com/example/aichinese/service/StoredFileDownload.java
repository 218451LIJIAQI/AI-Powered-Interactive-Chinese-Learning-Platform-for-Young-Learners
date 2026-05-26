package com.example.aichinese.service;

import java.util.Objects;

import org.springframework.core.io.Resource;

/**
 * Carrier for a managed file download — the {@link Resource} the
 * controller streams plus the {@code originalFileName} and
 * {@code contentType} required to set the correct {@code Content-Disposition}
 * and {@code Content-Type} response headers.
 *
 * <p>Built by {@link UploadStorageSupport#loadManagedFile} and returned
 * by storage services (e.g. {@link RecordedVideoStorageService}).
 * Keeps the controller free of storage-tier concerns and centralises
 * the path resolution + access-control checks in the storage layer.</p>
 */
public record StoredFileDownload(
        Resource resource,
        String originalFileName,
        String contentType) {

    public StoredFileDownload {
        Objects.requireNonNull(resource, "resource");
    }
}
