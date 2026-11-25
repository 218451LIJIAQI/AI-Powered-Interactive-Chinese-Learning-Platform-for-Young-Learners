package com.example.aichinese.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.support.TestTransactions;

@ExtendWith(OutputCaptureExtension.class)
class UploadStorageSupportTests {

    private static final String CONTENT = "demo";
    private static final String STORED_FILE_NAME = "stored.pdf";
    private static final String ESCAPING_FILE_NAME = "../outside.pdf";
    private static final String BLANK_STORED_FILE_NAME = " ";
    private static final String STORAGE_FAILED_MESSAGE = "Storage failed.";
    private static final String FILE_NOT_FOUND_MESSAGE = "File not found.";
    private static final String SENSITIVE_DIRECTORY = "sensitive-server-root";
    private static final String NESTED_FILE_NAME = "nested.txt";
    private static final String NESTED_FILE_CONTENT = "keeps the directory non-empty";
    private static final String SAFE_DELETION_LOG = "Failed to delete stored upload " + STORED_FILE_NAME;

    @TempDir
    Path tempDir;

    /** A real transaction lifecycle (synchronizations, commit, rollback) without a database behind it. */
    private final TransactionTemplate transaction = TestTransactions.synchronizingTemplate();

    @Test
    void interruptedCopyRemovesItsPartialFile() {
        MockMultipartFile upload = new MockMultipartFile("file", new byte[] {1, 2}) {
            @Override
            public InputStream getInputStream() {
                return new InputStream() {
                    private boolean first = true;

                    @Override
                    public int read() throws IOException {
                        if (first) {
                            first = false;
                            return 1;
                        }
                        throw new IOException("Upload interrupted");
                    }
                };
            }
        };
        assertThatThrownBy(() -> UploadStorageSupport.storeMultipartFile(
                upload, tempDir, STORED_FILE_NAME, STORAGE_FAILED_MESSAGE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(STORAGE_FAILED_MESSAGE);
        assertThat(tempDir.resolve(STORED_FILE_NAME)).doesNotExist();
    }

    @Test
    void failedCopyDoesNotOverwriteOrDeleteAnExistingTarget() throws IOException {
        Path existing = Files.writeString(tempDir.resolve(STORED_FILE_NAME), CONTENT);
        assertThatThrownBy(() -> UploadStorageSupport.storeMultipartFile(
                new MockMultipartFile("file", new byte[] {1, 2}), tempDir, STORED_FILE_NAME, STORAGE_FAILED_MESSAGE))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(existing).hasContent(CONTENT);
    }

    @Test
    void rollingBackDeletionKeepsTheStoredFile() throws IOException {
        Path existing = Files.writeString(tempDir.resolve(STORED_FILE_NAME), CONTENT);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            deleteSingleManagedFile(STORED_FILE_NAME);
            throw new IllegalStateException("Later database operation failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(existing).hasContent(CONTENT);
    }

    @Test
    void deletionWaitsForTheTransactionToCommit() throws IOException {
        Path existing = Files.writeString(tempDir.resolve(STORED_FILE_NAME), CONTENT);
        transaction.executeWithoutResult(status -> {
            deleteSingleManagedFile(STORED_FILE_NAME);
            assertThat(existing).hasContent(CONTENT);
        });
        assertThat(existing).doesNotExist();
    }

    @Test
    void deletionWithoutATransactionStillRemovesTheFile() throws IOException {
        Path existing = Files.writeString(tempDir.resolve(STORED_FILE_NAME), CONTENT);
        deleteSingleManagedFile(STORED_FILE_NAME);
        assertThat(existing).doesNotExist();
    }

    private void deleteSingleManagedFile(String storedFileName) {
        UploadStorageSupport.deleteManagedFileRefs(
                tempDir.toString(),
                List.of(UploadStorageSupport.ManagedFileRef.of(uploadedFile(storedFileName))));
    }

    @Test
    void storeMultipartFileClosesTheUploadStreamAfterCopying() throws IOException {
        CloseTrackingMultipartFile multipartFile = new CloseTrackingMultipartFile();

        Path storedPath = UploadStorageSupport.storeMultipartFile(
                multipartFile,
                tempDir,
                STORED_FILE_NAME,
                STORAGE_FAILED_MESSAGE);

        assertThat(Files.readString(storedPath, StandardCharsets.UTF_8)).isEqualTo(CONTENT);
        assertThat(multipartFile.openedStreams).hasSize(1);
        assertThat(multipartFile.openedStreams.getFirst().closed).isTrue();
    }

    @Test
    void storeMultipartFileRejectsManagedPathEscape() {
        CloseTrackingMultipartFile multipartFile = new CloseTrackingMultipartFile();

        assertThatThrownBy(() -> UploadStorageSupport.storeMultipartFile(
                multipartFile,
                tempDir,
                ESCAPING_FILE_NAME,
                STORAGE_FAILED_MESSAGE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(UploadStorageSupport.INVALID_UPLOAD_TARGET_MESSAGE);

        assertThat(multipartFile.openedStreams).isEmpty();
    }

    @Test
    void deleteQuietlyDoesNotExposeAbsolutePathWhenCleanupFails(CapturedOutput output) throws Exception {
        Path managedDirectory = tempDir.resolve(SENSITIVE_DIRECTORY).resolve(STORED_FILE_NAME);
        Files.createDirectories(managedDirectory);
        Files.writeString(managedDirectory.resolve(NESTED_FILE_NAME), NESTED_FILE_CONTENT);

        UploadStorageSupport.deleteQuietly(managedDirectory);

        assertThat(output)
                .contains(SAFE_DELETION_LOG)
                .contains("Exception")
                .doesNotContain(tempDir.toString())
                .doesNotContain(SENSITIVE_DIRECTORY)
                .doesNotContain(NESTED_FILE_NAME)
                .doesNotContain("at com.example.aichinese");
    }

    @Test
    void loadManagedFileReturnsNotFoundForMissingBlankOrEscapingMetadata() {
        assertNotFoundWhenLoading(null);
        assertNotFoundWhenLoading(uploadedFile(BLANK_STORED_FILE_NAME));
        assertNotFoundWhenLoading(uploadedFile(ESCAPING_FILE_NAME));
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

    @Test
    void mp4SignatureAcceptsCompatibleBrandsAndRejectsAudioOnlyM4aOrInvalidBoxSize() {
        // Major brand unknown ("xxxx"), compatible brand "iso6" -> valid ISO 14496-12 MP4 box.
        byte[] compatibleIso6 = new byte[] {
                0x00, 0x00, 0x00, 0x18,
                'f', 't', 'y', 'p',
                'x', 'x', 'x', 'x',
                0x00, 0x00, 0x00, 0x00,
                'i', 's', 'o', '6',
                'h', 'v', 'c', '1'
        };
        assertThat(UploadStorageSupport.FileSignature.MP4.matches(compatibleIso6)).isTrue();

        // Audio-only M4A brand without a video brand must not match MP4 video.
        byte[] m4aOnly = new byte[] {
                0x00, 0x00, 0x00, 0x14,
                'f', 't', 'y', 'p',
                'M', '4', 'A', ' ',
                0x00, 0x00, 0x00, 0x00,
                'M', '4', 'A', ' '
        };
        assertThat(UploadStorageSupport.FileSignature.MP4.matches(m4aOnly)).isFalse();

        // Malformed ftyp box size (not >= 16 and 4-byte aligned) is rejected.
        byte[] badBoxSize = new byte[] {
                0x00, 0x00, 0x00, 0x13,
                'f', 't', 'y', 'p',
                'i', 's', 'o', 'm',
                0x00, 0x00, 0x00, 0x00,
                'i', 's', 'o', 'm'
        };
        assertThat(UploadStorageSupport.FileSignature.MP4.matches(badBoxSize)).isFalse();
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

    private static UploadedFile uploadedFile(String storedFileName) {
        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setStoredFileName(storedFileName);
        return uploadedFile;
    }

    private static final class CloseTrackingMultipartFile extends MockMultipartFile {

        private final List<CloseTrackingInputStream> openedStreams = new ArrayList<>();

        private CloseTrackingMultipartFile() {
            super("file", CONTENT.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getInputStream() throws IOException {
            CloseTrackingInputStream stream = new CloseTrackingInputStream(getBytes());
            openedStreams.add(stream);
            return stream;
        }
    }

    private static final class CloseTrackingInputStream extends ByteArrayInputStream {

        private boolean closed;

        private CloseTrackingInputStream(byte[] content) {
            super(content);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
