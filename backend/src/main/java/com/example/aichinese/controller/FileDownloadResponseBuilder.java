package com.example.aichinese.controller;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;

import com.example.aichinese.service.StoredFileDownload;
import com.example.aichinese.util.FileNames;
import com.example.aichinese.util.TextUtils;

/**
 * Response for a private file download. {@code Cache-Control: no-store} and {@code Pragma: no-cache} are not set
 * here: ApiInterceptorConfig already sets them once on every route that serves a private file (under
 * /api/v1/students, /teachers and /admin), and a second copy from the response entity would repeat each header.
 */
final class FileDownloadResponseBuilder {

    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_OCTET_STREAM;

    private static final String DEFAULT_FILE_NAME = "download";
    private static final String CROSS_ORIGIN_RESOURCE_POLICY = "Cross-Origin-Resource-Policy";
    private static final String SAME_ORIGIN = "same-origin";
    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String NOSNIFF = "nosniff";

    private FileDownloadResponseBuilder() {
    }

    /** A CSV export as an attachment; the byte-order mark lets spreadsheet apps read it as UTF-8 (Chinese names). */
    static ResponseEntity<byte[]> csvAttachment(String fileName, String csv) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName)
                        .build()
                        .toString())
                .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    static ResponseEntity<Resource> buildInlineDownloadResponse(StoredFileDownload download) {
        return buildDownloadResponse(download, true);
    }

    static ResponseEntity<Resource> buildAttachmentDownloadResponse(StoredFileDownload download) {
        return buildDownloadResponse(download, false);
    }

    private static ResponseEntity<Resource> buildDownloadResponse(
            StoredFileDownload download,
            boolean requestedInline) {
        Resource resource = download.resource();
        MediaType mediaType = resolveMediaType(download);
        boolean inline = requestedInline && isSafeInlineMediaType(mediaType);
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(resolveFileName(download), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(inline || !isExecutableOrStructuredText(mediaType) ? mediaType : DEFAULT_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(X_CONTENT_TYPE_OPTIONS, NOSNIFF)
                .header(HttpHeaders.EXPIRES, "Thu, 01 Jan 1970 00:00:00 GMT")
                .header(CROSS_ORIGIN_RESOURCE_POLICY, SAME_ORIGIN)
                .body(resource);
    }

    private static MediaType resolveMediaType(StoredFileDownload download) {
        String contentType = download.contentType();
        if (contentType != null) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (InvalidMediaTypeException exception) {
                return DEFAULT_MEDIA_TYPE;
            }
        }
        return MediaTypeFactory.getMediaType(download.resource()).orElse(DEFAULT_MEDIA_TYPE);
    }

    private static boolean isSafeInlineMediaType(MediaType mediaType) {
        return MediaType.APPLICATION_PDF.includes(mediaType)
                || MediaType.TEXT_PLAIN.includes(mediaType)
                || "audio".equals(mediaType.getType())
                || "video".equals(mediaType.getType())
                || ("image".equals(mediaType.getType()) && !"svg+xml".equalsIgnoreCase(mediaType.getSubtype()));
    }

    /** Never served under its own type as an attachment: the browser could render or run it. */
    private static boolean isExecutableOrStructuredText(MediaType mediaType) {
        String subtype = mediaType.getSubtype().toLowerCase(Locale.ROOT);
        return MediaType.TEXT_HTML.includes(mediaType)
                || MediaType.APPLICATION_XHTML_XML.includes(mediaType)
                || MediaType.APPLICATION_XML.includes(mediaType)
                || MediaType.TEXT_XML.includes(mediaType)
                || MediaType.APPLICATION_JSON.includes(mediaType)
                || subtype.endsWith("+xml")
                || subtype.endsWith("+json")
                || subtype.contains("javascript");
    }

    private static String resolveFileName(StoredFileDownload download) {
        String originalFileName = download.originalFileName();
        if (originalFileName != null) {
            return sanitizeFileName(originalFileName);
        }
        String resourceFileName = TextUtils.trimToNull(download.resource().getFilename());
        return resourceFileName != null ? sanitizeFileName(resourceFileName) : DEFAULT_FILE_NAME;
    }

    private static String sanitizeFileName(String value) {
        String cleaned = FileNames.sanitize(value);
        return cleaned.isEmpty() ? DEFAULT_FILE_NAME : cleaned;
    }
}
