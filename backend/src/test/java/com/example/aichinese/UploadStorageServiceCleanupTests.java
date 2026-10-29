package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.example.aichinese.config.UploadProperties;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.UploadedFileRepository;
import com.example.aichinese.service.FileStorageService;
import com.example.aichinese.support.TestUploadFixtures;

/**
 * An upload whose metadata row cannot be saved deletes the file it already stored; a practice-check upload is never
 * written to disk at all.
 */
class UploadStorageServiceCleanupTests {

    private static final String METADATA_FAILURE_MESSAGE = "metadata failed";

    private final UploadedFileRepository uploadedFileRepository = mock(UploadedFileRepository.class);

    @TempDir
    Path uploadRoot;

    @Test
    void verificationDocumentUploadDeletesStoredObjectWhenMetadataSaveFails() throws IOException {
        assertStoredObjectDeletedWhenMetadataSaveFails(() -> storageService()
                .storeVerificationDocument(multipart("verification.pdf", "application/pdf", TestUploadFixtures.minimalPdf()), user()));
    }

    @Test
    void recordedVideoUploadDeletesStoredObjectWhenMetadataSaveFails() throws IOException {
        assertStoredObjectDeletedWhenMetadataSaveFails(() -> storageService()
                .storeRecordedVideo(multipart("lesson.mp4", "video/mp4", TestUploadFixtures.minimalMp4()), user()));
    }

    @Test
    void practiceCheckUploadsAreOnlyReadIntoMemory() throws IOException {
        FileStorageService.PracticeUpload upload = storageService()
                .readPracticeAudio(multipart("voice.webm", "audio/webm", TestUploadFixtures.minimalWebm()));

        assertThat(upload.data()).isEqualTo(TestUploadFixtures.minimalWebm());
        assertThat(upload.fileName()).isEqualTo("voice.webm");
        assertThat(upload.contentType()).isEqualTo("audio/webm");
        try (Stream<Path> files = Files.walk(uploadRoot)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    /** Saving the metadata fails after the file was written (reaching save proves it), so nothing is left on disk. */
    private void assertStoredObjectDeletedWhenMetadataSaveFails(ThrowingCallable upload) throws IOException {
        doThrow(new IllegalStateException(METADATA_FAILURE_MESSAGE)).when(uploadedFileRepository).save(any(UploadedFile.class));

        assertThatThrownBy(upload)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(METADATA_FAILURE_MESSAGE);

        try (Stream<Path> files = Files.walk(uploadRoot)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    private FileStorageService storageService() {
        UploadProperties properties = new UploadProperties();
        properties.setDirectory(uploadRoot.resolve("uploads").toString());
        properties.setRecordedVideoDirectory(uploadRoot.resolve("videos").toString());
        return new FileStorageService(uploadedFileRepository, properties);
    }

    private static MockMultipartFile multipart(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content);
    }

    private static User user() {
        User user = new User();
        user.setId(1L);
        user.setEmail("teacher@example.com");
        user.setDisplayName("Teacher");
        user.setPasswordHash("hash");
        return user;
    }
}
