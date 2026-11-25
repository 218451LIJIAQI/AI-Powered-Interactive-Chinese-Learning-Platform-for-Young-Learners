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
 * /api/v1/students and /api/v1/admin), and a second copy from the response entity would repeat each header.
 */
final class FileDownloadResponseBuilder {

    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_OCTET_STREAM;

    private FileDownloadResponseBuilder() {
    }

    /** A CSV export as an attachment; the byte-order mark lets spreadsheet apps read it as UTF-8 (Chinese names). */
    static ResponseEntity<byte[]> csvAttachment(String fileName, String csv) {
        String safeFileName = sanitizeFileName(fileName, "export.csv");
        return privateDownloadHeaders(ResponseEntity.ok())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(safeFileName, StandardCharsets.UTF_8)
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
        MediaType mediaType = resolveMediaType(download);
        boolean safeContentType = !isExecutableOrStructuredText(mediaType);
        boolean inline = requestedInline && safeContentType && isSafeInlineMediaType(mediaType);
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(resolveFileName(download), StandardCharsets.UTF_8)
                .build();

        return privateDownloadHeaders(ResponseEntity.ok())
                .contentType(safeContentType ? mediaType : DEFAULT_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }

    private static ResponseEntity.BodyBuilder privateDownloadHeaders(ResponseEntity.BodyBuilder builder) {
        return builder
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.EXPIRES, "Thu, 01 Jan 1970 00:00:00 GMT")
                .header("Cross-Origin-Resource-Policy", "same-origin");
    }

    private static MediaType resolveMediaType(StoredFileDownload download) {
        String contentType = download.contentType();
        if (contentType != null) {
            try {
                MediaType parsed = MediaType.parseMediaType(contentType);
                return parsed.isConcrete() ? parsed : DEFAULT_MEDIA_TYPE;
            } catch (InvalidMediaTypeException exception) {
                return DEFAULT_MEDIA_TYPE;
            }
        }
        return MediaTypeFactory.getMediaType(download.resource())
                .filter(MediaType::isConcrete)
                .orElse(DEFAULT_MEDIA_TYPE);
    }

    private static boolean isSafeInlineMediaType(MediaType mediaType) {
        String subtype = mediaType.getSubtype().toLowerCase(Locale.ROOT);
        return MediaType.APPLICATION_PDF.includes(mediaType)
                || MediaType.TEXT_PLAIN.includes(mediaType)
                || "audio".equals(mediaType.getType())
                || "video".equals(mediaType.getType())
                || ("image".equals(mediaType.getType()) && !subtype.contains("svg"));
    }

    /** Never served under its own type: the browser could render or run it. */
    private static boolean isExecutableOrStructuredText(MediaType mediaType) {
        String subtype = mediaType.getSubtype().toLowerCase(Locale.ROOT);
        return MediaType.TEXT_HTML.includes(mediaType)
                || MediaType.APPLICATION_XHTML_XML.includes(mediaType)
                || MediaType.APPLICATION_XML.includes(mediaType)
                || MediaType.TEXT_XML.includes(mediaType)
                || MediaType.APPLICATION_JSON.includes(mediaType)
                || subtype.endsWith("+xml")
                || subtype.endsWith("+json")
                || subtype.contains("svg")
                || subtype.contains("javascript")
                || subtype.contains("ecmascript");
    }

    private static String resolveFileName(StoredFileDownload download) {
        String fileName = download.originalFileName() != null
                ? download.originalFileName()
                : TextUtils.trimToNull(download.resource().getFilename());
        return sanitizeFileName(fileName, "download");
    }

    private static String sanitizeFileName(String fileName, String fallback) {
        String cleaned = fileName == null ? "" : FileNames.sanitize(fileName);
        return cleaned.isEmpty() ? fallback : cleaned;
    }
}
