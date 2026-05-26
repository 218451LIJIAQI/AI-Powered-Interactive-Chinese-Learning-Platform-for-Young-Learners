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

    private static final String DEFAULT_FILE_NAME = "download";

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
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "Thu, 01 Jan 1970 00:00:00 GMT")
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .body(resource);
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

    private static boolean isSafeInlineMediaType(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
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
        if (mediaType == null || isExecutableOrStructuredText(mediaType)) {
            return DEFAULT_MEDIA_TYPE;
        }
        return mediaType;
    }

    private static boolean isExecutableOrStructuredText(MediaType mediaType) {
        String subtype = mediaType.getSubtype() == null ? "" : mediaType.getSubtype().toLowerCase(Locale.ROOT);
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
        return cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned) ? DEFAULT_FILE_NAME : cleaned;
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
