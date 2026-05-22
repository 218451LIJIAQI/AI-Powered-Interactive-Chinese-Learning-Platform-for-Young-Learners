package com.example.aichinese.controller;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;

import com.example.aichinese.service.StoredFileDownload;

final class FileDownloadResponseBuilder {

    @NonNull
    private static final MediaType DEFAULT_MEDIA_TYPE = Objects.requireNonNull(MediaType.APPLICATION_OCTET_STREAM);

    private FileDownloadResponseBuilder() {
    }

    static ResponseEntity<Resource> buildInlineDownloadResponse(StoredFileDownload download) {
        return buildDownloadResponse(download, ContentDisposition.inline());
    }

    static ResponseEntity<Resource> buildAttachmentDownloadResponse(StoredFileDownload download) {
        return buildDownloadResponse(download, ContentDisposition.attachment());
    }

    private static ResponseEntity<Resource> buildDownloadResponse(
            StoredFileDownload download,
            ContentDisposition.Builder dispositionBuilder) {
        ContentDisposition disposition = dispositionBuilder
                .filename(resolveFileName(download), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(resolveMediaType(download))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(download.resource());
    }

    private static @NonNull MediaType resolveMediaType(StoredFileDownload download) {
        String contentType = trimToNull(download.contentType());
        if (contentType != null) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (InvalidMediaTypeException exception) {
                return DEFAULT_MEDIA_TYPE;
            }
        }
        Optional<MediaType> detectedMediaType = MediaTypeFactory.getMediaType(download.resource());
        return detectedMediaType.isPresent() ? Objects.requireNonNull(detectedMediaType.get()) : DEFAULT_MEDIA_TYPE;
    }

    private static String resolveFileName(StoredFileDownload download) {
        String originalFileName = trimToNull(download.originalFileName());
        if (originalFileName != null) {
            return originalFileName;
        }
        String resourceFileName = trimToNull(download.resource().getFilename());
        return resourceFileName != null ? resourceFileName : "download";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
