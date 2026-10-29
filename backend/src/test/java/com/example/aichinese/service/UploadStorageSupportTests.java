package com.example.aichinese.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.UploadedFile;

@ExtendWith(OutputCaptureExtension.class)
class UploadStorageSupportTests {

    private static final String ORIGINAL_FILE_NAME = "lesson.pdf";
    private static final String CONTENT_TYPE = "application/pdf";
    private static final String CONTENT = "demo";
    private static final String STORED_FILE_NAME = "stored.pdf";
    private static final String ESCAPING_FILE_NAME = "../outside.pdf";
    private static final String BLANK_STORED_FILE_NAME = " ";
    private static final String STORAGE_FAILED_MESSAGE = "Storage failed.";
    private static final String FILE_NOT_FOUND_MESSAGE = "File not found.";
    private static final String SENSITIVE_DIRECTORY = "sensitive-server-root";
    private static final String NESTED_FILE_NAME = "nested.txt";
    private static final String NESTED_FILE_CONTENT = "keeps the directory non-empty";
    private static final String SAFE_ROLLBACK_LOG =
            "Failed to delete stored object during upload rollback for name " + STORED_FILE_NAME;

    @TempDir
    Path tempDir;

    @Test
    void storeMultipartFileClosesTheUploadStreamAfterCopying() throws IOException {
        CloseTrackingMultipartFile multipartFile = multipartFile();

        Path storedPath = UploadStorageSupport.storeMultipartFile(
                multipartFile,
                tempDir,
                STORED_FILE_NAME,
                STORAGE_FAILED_MESSAGE);

        assertThat(Files.readString(storedPath, StandardCharsets.UTF_8)).isEqualTo(CONTENT);
        assertThat(multipartFile.openedStreams()).hasSize(1);
        assertThat(multipartFile.openedStreams().getFirst().closed()).isTrue();
    }

    @Test
    void storeMultipartFileRejectsManagedPathEscape() {
        CloseTrackingMultipartFile multipartFile = multipartFile();

        assertThatThrownBy(() -> UploadStorageSupport.storeMultipartFile(
                multipartFile,
                tempDir,
                ESCAPING_FILE_NAME,
                STORAGE_FAILED_MESSAGE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(UploadStorageSupport.INVALID_UPLOAD_TARGET_MESSAGE);

        assertThat(multipartFile.openedStreams()).isEmpty();
    }

    @Test
    void deleteQuietlyDoesNotExposeAbsolutePathWhenCleanupFails(CapturedOutput output) throws Exception {
        Path managedDirectory = tempDir.resolve(SENSITIVE_DIRECTORY).resolve(STORED_FILE_NAME);
        Files.createDirectories(managedDirectory);
        Files.writeString(managedDirectory.resolve(NESTED_FILE_NAME), NESTED_FILE_CONTENT);

        UploadStorageSupport.deleteQuietly(managedDirectory);

        assertThat(output)
                .contains(SAFE_ROLLBACK_LOG)
                .contains("Exception")
                .doesNotContain(tempDir.toString())
                .doesNotContain(SENSITIVE_DIRECTORY)
                .doesNotContain(NESTED_FILE_NAME)
                .doesNotContain("at com.example.aichinese");
    }

    @Test
    void loadManagedFileReturnsNotFoundForMissingOrBlankMetadata() {
        assertNotFoundWhenLoading(null);

        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setStoredFileName(BLANK_STORED_FILE_NAME);

        assertNotFoundWhenLoading(uploadedFile);
    }

    @Test
    void loadManagedFileReturnsNotFoundForEscapingStoredFileName() {
        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setStoredFileName(ESCAPING_FILE_NAME);

        assertNotFoundWhenLoading(uploadedFile);
    }

    @Test
    void extractSafeOriginalFileNameNormalizesPathSegmentsAcrossPlatforms() {
        assertThat(UploadStorageSupport.extractSafeOriginalFileName("C:\\fakepath\\certificate.pdf"))
                .isEqualTo("certificate.pdf");
        assertThat(UploadStorageSupport.extractSafeOriginalFileName("/tmp/uploads/lesson notes.pdf"))
                .isEqualTo("lesson notes.pdf");
    }

    @Test
    void extractSafeOriginalFileNameRemovesDangerousCharactersAndLeadingDots() {
        assertThat(UploadStorageSupport.extractSafeOriginalFileName("folder\\\u202eevil:report?.pdf"))
                .isEqualTo("_evil_report_.pdf");
        assertThat(UploadStorageSupport.extractSafeOriginalFileName("../.env"))
                .isEqualTo("env");
    }

    @Test
    void extractSafeOriginalFileNameRejectsBlankPathSegments() {
        assertThatThrownBy(() -> UploadStorageSupport.extractSafeOriginalFileName("folder\\"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(UploadStorageSupport.INVALID_FILE_NAME_MESSAGE);
    }

    @Test
    void extractSafeOriginalFileNameRejectsNamesLongerThanTheStoredColumn() {
        String longestStorableName = "a".repeat(251) + ".pdf";

        assertThat(UploadStorageSupport.extractSafeOriginalFileName(longestStorableName))
                .isEqualTo(longestStorableName);
        assertThatThrownBy(() -> UploadStorageSupport.extractSafeOriginalFileName("a" + longestStorableName))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo(UploadStorageSupport.INVALID_FILE_NAME_MESSAGE);
                });
    }

    private void assertNotFoundWhenLoading(UploadedFile uploadedFile) {
        assertThatThrownBy(() -> UploadStorageSupport.loadManagedFile(
                tempDir.toString(),
                uploadedFile,
                FILE_NOT_FOUND_MESSAGE))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo(FILE_NOT_FOUND_MESSAGE);
                });
    }

    private static CloseTrackingMultipartFile multipartFile() {
        return new CloseTrackingMultipartFile(ORIGINAL_FILE_NAME, CONTENT_TYPE, CONTENT);
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
