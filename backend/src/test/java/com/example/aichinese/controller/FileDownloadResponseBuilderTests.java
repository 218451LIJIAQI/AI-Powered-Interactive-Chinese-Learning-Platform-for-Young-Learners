package com.example.aichinese.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.example.aichinese.service.StoredFileDownload;

/**
 * Unit coverage for {@code FileDownloadResponseBuilder} - pins the MIME fallback
 * chain (stored content-type to media-type sniff to {@code application/octet-stream}),
 * invalid-content-type tolerance, UTF-8 filename encoding, and the private-download
 * headers. {@code Cache-Control: no-store} comes once from ApiInterceptorConfig
 * (checked by the download integration tests), so the builder must not add a second copy.
 */
class FileDownloadResponseBuilderTests {

    private static final String CONTENT_DISPOSITION = "Content-Disposition";
    private static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String CROSS_ORIGIN_RESOURCE_POLICY = "Cross-Origin-Resource-Policy";
    private static final String NO_SNIFF = "nosniff";
    private static final String SAME_ORIGIN = "same-origin";

    @Test
    void invalidStoredContentTypeFallsBackToOctetStream() {
        StoredFileDownload download = download("lesson.mp4", "not a valid content type");

        ResponseEntity<Resource> response = FileDownloadResponseBuilder.buildInlineDownloadResponse(download);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertPrivateDownloadHeaders(response);
        assertThat(response.getHeaders().getFirst(CONTENT_DISPOSITION)).contains("lesson.mp4");
        assertThat(response.getBody()).isSameAs(download.resource());
    }

    @Test
    void missingStoredContentTypeIsSniffedFromTheResourceFileName() {
        Resource video = new ByteArrayResource(bytes("demo")) {
            @Override
            public String getFilename() {
                return "lesson.mp4";
            }
        };

        ResponseEntity<Resource> sniffed = FileDownloadResponseBuilder.buildInlineDownloadResponse(
                new StoredFileDownload(video, "lesson.mp4", null));
        // A ByteArrayResource has no file name, so nothing can be sniffed and the octet-stream default applies.
        ResponseEntity<Resource> unknown = FileDownloadResponseBuilder.buildInlineDownloadResponse(
                new StoredFileDownload(resource("demo"), "notes", " "));

        assertThat(sniffed.getHeaders().getContentType()).isEqualTo(MediaType.valueOf("video/mp4"));
        assertThat(sniffed.getHeaders().getFirst(CONTENT_DISPOSITION)).startsWith("inline;");
        assertThat(unknown.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    void blankStoredFileNameUsesResourceFileNameFallback() {
        Resource resource = new ByteArrayResource(bytes("demo")) {
            @Override
            public String getFilename() {
                return "fallback.pdf";
            }
        };
        StoredFileDownload download = new StoredFileDownload(resource, " ", "application/pdf");

        ResponseEntity<Resource> response = FileDownloadResponseBuilder.buildInlineDownloadResponse(download);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(CONTENT_DISPOSITION)).contains("fallback.pdf");
    }

    @Test
    void attachmentDownloadUsesAttachmentDisposition() {
        StoredFileDownload download = download("verification-document.pdf", "application/pdf");

        ResponseEntity<Resource> response = FileDownloadResponseBuilder.buildAttachmentDownloadResponse(download);

        assertThat(response.getHeaders().getFirst(CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .contains("verification-document.pdf");
        assertPrivateDownloadHeaders(response);
    }

    @Test
    void unsafeFileNameCharactersAreSanitizedBeforeEnteringHeaders() {
        StoredFileDownload download = download("..\\private/lesson\r\nX-Injected: yes.pdf", "application/pdf");

        ResponseEntity<Resource> response = FileDownloadResponseBuilder.buildAttachmentDownloadResponse(download);

        String contentDisposition = response.getHeaders().getFirst(CONTENT_DISPOSITION);
        assertThat(contentDisposition)
                .doesNotContain("\r", "\n", "\\", "/", "X-Injected: yes")
                .contains("filename*=UTF-8''_private_lesson_X-Injected_%20yes.pdf");
        assertThat(response.getHeaders().getFirst(CONTENT_TYPE_OPTIONS)).isEqualTo(NO_SNIFF);
    }

    @Test
    void unsafeInlineMediaTypeFallsBackToAttachmentAndOctetStream() {
        StoredFileDownload download = new StoredFileDownload(
                resource("<script>alert(1)</script>"),
                "preview.html",
                "text/html");

        ResponseEntity<Resource> response = FileDownloadResponseBuilder.buildInlineDownloadResponse(download);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getFirst(CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .contains("preview.html");
        assertThat(response.getHeaders().getFirst(CROSS_ORIGIN_RESOURCE_POLICY)).isEqualTo(SAME_ORIGIN);
        assertThat(response.getHeaders().getExpires()).isZero();
    }

    @Test
    void storedFileDownloadRequiresResource() {
        assertThatNullPointerException()
                .isThrownBy(() -> new StoredFileDownload(null, "file.pdf", "application/pdf"))
                .withMessage("resource");
    }

    private static StoredFileDownload download(String filename, String contentType) {
        return new StoredFileDownload(resource("demo"), filename, contentType);
    }

    private static ByteArrayResource resource(String content) {
        return new ByteArrayResource(bytes(content));
    }

    private static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static void assertPrivateDownloadHeaders(ResponseEntity<Resource> response) {
        assertThat(response.getHeaders().getCacheControl()).isNull();
        assertThat(response.getHeaders().getPragma()).isNull();
        assertThat(response.getHeaders().getExpires()).isZero();
        assertThat(response.getHeaders().getFirst(CONTENT_TYPE_OPTIONS)).isEqualTo(NO_SNIFF);
    }
}
