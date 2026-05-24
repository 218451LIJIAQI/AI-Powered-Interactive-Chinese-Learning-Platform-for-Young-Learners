package com.example.aichinese.controller;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

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

/**
 * Package-private helper that converts a {@link StoredFileDownload}
 * (the storage-tier result) into the {@link ResponseEntity} the
 * controller layer streams back to the client.
 *
 * <h2>What it standardises</h2>
 * <ul>
 *   <li><b>Content-Disposition</b> — chooses {@code inline} (preview)
 *       or {@code attachment} (force download) with UTF-8 filename
 *       encoding so non-ASCII names (e.g. Chinese) survive the
 *       header round-trip.</li>
 *   <li><b>Content-Type</b> — prefers the stored MIME hint; falls
 *       back to {@link MediaTypeFactory} probing; defaults to
 *       {@code application/octet-stream} so the browser never sniffs
 *       the body and gets tricked into executing it.</li>
 *   <li><b>Cache hygiene</b> — emits {@code Cache-Control: no-store}
 *       + {@code Pragma: no-cache} so private artefacts (verification
 *       documents, AI uploads, paid lesson videos) never land in
 *       intermediate caches or browser disk.</li>
 *   <li><b>Safe fallback file name</b> — if the original is missing
 *       or empty, falls back to the resource's filename, then a
 *       literal {@code "download"}; never lets a null leak through.</li>
 * </ul>
 *
 * <h2>Why a helper instead of a base controller</h2>
 * <p>Multiple controllers stream files (admin, teacher, student
 * surfaces); centralising the response shape ensures each surface
 * gets identical caching / encoding / MIME behaviour without forcing
 * a shared base class.</p>
 */
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
        return Objects.requireNonNull(MediaTypeFactory.getMediaType(download.resource()).orElse(DEFAULT_MEDIA_TYPE));
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
