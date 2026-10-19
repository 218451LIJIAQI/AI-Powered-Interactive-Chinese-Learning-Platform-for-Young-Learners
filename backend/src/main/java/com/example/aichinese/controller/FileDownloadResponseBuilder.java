package com.example.aichinese.controller;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;

import com.example.aichinese.service.StoredFileDownload;

final class FileDownloadResponseBuilder {

    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_OCTET_STREAM;

    private static final String DEFAULT_FILE_NAME = "download";
    private static final String CROSS_ORIGIN_RESOURCE_POLICY = "Cross-Origin-Resource-Policy";
    private static final String SAME_ORIGIN = "same-origin";
    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String NOSNIFF = "nosniff";

    private FileDownloadResponseBuilder() {
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
        Objects.requireNonNull(download, "download");
        Resource resource = Objects.requireNonNull(download.resource(), "download.resource");
        MediaType mediaType = resolveMediaType(download);
        boolean inline = requestedInline && isSafeInlineMediaType(mediaType);
        ContentDisposition.Builder dispositionBuilder = inline
                ? ContentDisposition.inline()
                : ContentDisposition.attachment();
        ContentDisposition disposition = dispositionBuilder
                .filename(resolveFileName(download), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(inline ? mediaType : safeAttachmentMediaType(mediaType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(X_CONTENT_TYPE_OPTIONS, NOSNIFF)
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "Thu, 01 Jan 1970 00:00:00 GMT")
                .header(CROSS_ORIGIN_RESOURCE_POLICY, SAME_ORIGIN)
                .body(resource);
    }

    private static MediaType resolveMediaType(StoredFileDownload download) {
        String contentType = trimToNull(download.contentType());
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
        if (MediaType.APPLICATION_PDF.includes(mediaType) || MediaType.TEXT_PLAIN.includes(mediaType)) {
            return true;
        }
        if ("audio".equals(mediaType.getType()) || "video".equals(mediaType.getType())) {
            return true;
        }
        return "image".equals(mediaType.getType())
                && !"svg+xml".equalsIgnoreCase(mediaType.getSubtype());
    }

    private static MediaType safeAttachmentMediaType(MediaType mediaType) {
        if (isExecutableOrStructuredText(mediaType)) {
            return DEFAULT_MEDIA_TYPE;
        }
        return mediaType;
    }

    private static boolean isExecutableOrStructuredText(MediaType mediaType) {
        String subtype = mediaType.getSubtype().toLowerCase(Locale.ROOT);
        if (MediaType.TEXT_HTML.includes(mediaType)
                || MediaType.APPLICATION_XHTML_XML.includes(mediaType)
                || MediaType.APPLICATION_XML.includes(mediaType)
                || MediaType.TEXT_XML.includes(mediaType)
                || MediaType.APPLICATION_JSON.includes(mediaType)) {
            return true;
        }
        return "svg+xml".equals(subtype)
                || subtype.endsWith("+xml")
                || subtype.endsWith("+json")
                || subtype.contains("javascript");
    }

    private static String resolveFileName(StoredFileDownload download) {
        String originalFileName = trimToNull(download.originalFileName());
        if (originalFileName != null) {
            return sanitizeFileName(originalFileName);
        }
        String resourceFileName = trimToNull(download.resource().getFilename());
        return resourceFileName != null ? sanitizeFileName(resourceFileName) : DEFAULT_FILE_NAME;
    }

    private static String sanitizeFileName(String value) {
        StringBuilder sanitized = new StringBuilder(value.length());
        boolean previousWasWhitespace = false;

        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (isUnsafeFileNameCodePoint(codePoint)) {
                appendReplacement(sanitized);
                previousWasWhitespace = false;
                continue;
            }

            if (Character.isWhitespace(codePoint)) {
                if (!previousWasWhitespace && !sanitized.isEmpty()) {
                    sanitized.append(' ');
                    previousWasWhitespace = true;
                }
                continue;
            }

            sanitized.appendCodePoint(codePoint);
            previousWasWhitespace = false;
        }

        String cleaned = sanitized.toString().trim();
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1).trim();
        }
        return cleaned.isEmpty() ? DEFAULT_FILE_NAME : cleaned;
    }

    private static boolean isUnsafeFileNameCodePoint(int codePoint) {
        return Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT
                || switch (codePoint) {
                    case '"', '*', '/', ':', '<', '>', '?', '\\', '|' -> true;
                    default -> false;
                };
    }

    private static void appendReplacement(StringBuilder sanitized) {
        if (sanitized.isEmpty() || sanitized.charAt(sanitized.length() - 1) != '_') {
            sanitized.append('_');
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
