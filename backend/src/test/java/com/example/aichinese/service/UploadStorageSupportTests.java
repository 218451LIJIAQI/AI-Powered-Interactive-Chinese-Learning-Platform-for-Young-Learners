package com.example.aichinese.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.UploadedFile;

class UploadStorageSupportTests {

    @TempDir
    Path tempDir;

    @Test
    void storeMultipartFileClosesTheUploadStreamAfterCopying() throws IOException {
        CloseTrackingMultipartFile multipartFile = new CloseTrackingMultipartFile("lesson.pdf", "application/pdf", "demo");

        Path storedPath = UploadStorageSupport.storeMultipartFile(
                multipartFile,
                tempDir,
                "stored.pdf",
                "Invalid target.",
                "Storage failed.");

        assertThat(Files.readString(storedPath, StandardCharsets.UTF_8)).isEqualTo("demo");
        assertThat(multipartFile.openedStreams()).hasSize(1);
        assertThat(multipartFile.openedStreams().getFirst().closed()).isTrue();
    }

    @Test
    void storeMultipartFileRejectsManagedPathEscape() {
        CloseTrackingMultipartFile multipartFile = new CloseTrackingMultipartFile("lesson.pdf", "application/pdf", "demo");

        assertThatThrownBy(() -> UploadStorageSupport.storeMultipartFile(
                multipartFile,
                tempDir,
                "../outside.pdf",
                "Invalid target.",
                "Storage failed."))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid target.");

        assertThat(multipartFile.openedStreams()).isEmpty();
    }

    @Test
    void loadManagedFileReturnsNotFoundForMissingOrBlankMetadata() {
        assertThatThrownBy(() -> UploadStorageSupport.loadManagedFile(tempDir.toString(), null, "File not found."))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("File not found.");
                });

        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setStoredFileName(" ");

        assertThatThrownBy(() -> UploadStorageSupport.loadManagedFile(tempDir.toString(), uploadedFile, "File not found."))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("File not found.");
                });
    }

    @Test
    void loadManagedFileReturnsNotFoundForEscapingStoredFileName() throws IOException {
        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setStoredFileName("../outside.pdf");

        assertThatThrownBy(() -> UploadStorageSupport.loadManagedFile(tempDir.toString(), uploadedFile, "File not found."))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("File not found.");
                });
    }

    @Test
    void extractSafeOriginalFileNameNormalizesPathSegmentsAcrossPlatforms() {
        assertThat(UploadStorageSupport.extractSafeOriginalFileName(
                "C:\\fakepath\\certificate.pdf",
                "Invalid file name."))
                .isEqualTo("certificate.pdf");
        assertThat(UploadStorageSupport.extractSafeOriginalFileName(
                "/tmp/uploads/lesson notes.pdf",
                "Invalid file name."))
                .isEqualTo("lesson notes.pdf");
    }

    @Test
    void extractSafeOriginalFileNameRemovesDangerousCharactersAndLeadingDots() {
        assertThat(UploadStorageSupport.extractSafeOriginalFileName(
                "folder\\\u202eevil:report?.pdf",
                "Invalid file name."))
                .isEqualTo("_evil_report_.pdf");
        assertThat(UploadStorageSupport.extractSafeOriginalFileName(
                "../.env",
                "Invalid file name."))
                .isEqualTo("env");
    }

    @Test
    void extractSafeOriginalFileNameRejectsBlankPathSegments() {
        assertThatThrownBy(() -> UploadStorageSupport.extractSafeOriginalFileName(
                "folder\\",
                "Invalid file name."))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid file name.");
    }

    private static final class CloseTrackingMultipartFile implements MultipartFile {

        private final String originalFilename;
        private final String contentType;
        private final byte[] content;
        private final List<CloseTrackingInputStream> openedStreams = new ArrayList<>();

        private CloseTrackingMultipartFile(String originalFilename, String contentType, String content) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        List<CloseTrackingInputStream> openedStreams() {
            return openedStreams;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content.clone();
        }

        @Override
        public InputStream getInputStream() {
            CloseTrackingInputStream stream = new CloseTrackingInputStream(content);
            openedStreams.add(stream);
            return stream;
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }

        @Override
        public void transferTo(Path dest) throws IOException {
            Files.write(dest, content);
        }
    }

    private static final class CloseTrackingInputStream extends ByteArrayInputStream {

        private boolean closed;

        private CloseTrackingInputStream(byte[] content) {
            super(content);
        }

        boolean closed() {
            return closed;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
