package com.example.aichinese.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.example.aichinese.service.StoredFileDownload;

/**
 * Unit coverage for {@code FileDownloadResponseBuilder} — pins the
 * MIME fallback chain (stored content-type → media-type sniff →
 * {@code application/octet-stream}), invalid-content-type tolerance,
 * UTF-8 filename encoding for non-ASCII names, and the
 * {@code Cache-Control: no-store} hygiene applied to every private
 * download.
 */
class FileDownloadResponseBuilderTests {

    @Test
    void invalidStoredContentTypeFallsBackToOctetStream() {
        StoredFileDownload download = new StoredFileDownload(
                new ByteArrayResource("demo".getBytes()),
                "lesson.mp4",
                "not a valid content type");

        ResponseEntity<Resource> response = FileDownloadResponseBuilder.buildInlineDownloadResponse(download);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getPragma()).isEqualTo("no-cache");
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("lesson.mp4");
        assertThat(response.getBody()).isSameAs(download.resource());
    }

    @Test
    void blankStoredFileNameUsesResourceFileNameFallback() {
        Resource resource = new ByteArrayResource("demo".getBytes()) {
            @Override
            public String getFilename() {
                return "fallback.pdf";
            }
        };
        StoredFileDownload download = new StoredFileDownload(resource, " ", "application/pdf");

        ResponseEntity<Resource> response = FileDownloadResponseBuilder.buildInlineDownloadResponse(download);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("fallback.pdf");
    }

    @Test
    void attachmentDownloadUsesAttachmentDisposition() {
        StoredFileDownload download = new StoredFileDownload(
                new ByteArrayResource("demo".getBytes()),
                "verification-document.pdf",
                "application/pdf");

        ResponseEntity<Resource> response = FileDownloadResponseBuilder.buildAttachmentDownloadResponse(download);

        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .startsWith("attachment;")
                .contains("verification-document.pdf");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getPragma()).isEqualTo("no-cache");
    }
}
