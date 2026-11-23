package com.example.aichinese.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.UploadProperties;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.UploadedFileRepository;
import com.example.aichinese.support.TestTransactions;
import com.example.aichinese.support.TestUploadFixtures;

import jakarta.persistence.EntityManager;

/**
 * An upload whose metadata row cannot be saved deletes the file it already stored; a practice-check upload is never
 * written to disk at all.
 */
class FileStorageServiceTests {

    private static final String METADATA_FAILURE_MESSAGE = "metadata failed";

    private final UploadedFileRepository uploadedFileRepository = mock(UploadedFileRepository.class);

    @TempDir
    Path uploadRoot;

    private final TransactionTemplate transaction = TestTransactions.synchronizingTemplate();

    @Test
    void supportScreenshotUploadDeletesStoredObjectWhenMetadataSaveFails() throws IOException {
        FileStorageService storage = storageService();
        assertStoredObjectDeletedWhenMetadataSaveFails(() -> {
            var screenshot = multipart("screen.png", "image/png", TestUploadFixtures.minimalPng());
            try (var staged = storage.stageSupportScreenshot(screenshot)) {
                storage.saveStagedUpload(staged, user());
            }
        });
    }

    @Test
    void discardedStagedSupportScreenshotLeavesNoStoredFile() throws IOException {
        FileStorageService storage = storageService();
        var staged = storage
                .stageSupportScreenshot(multipart("screen.png", "image/png", TestUploadFixtures.minimalPng()));
        staged.close();
        assertNoStoredFiles();
    }

    @Test
    void recordedVideoUploadDeletesStoredObjectWhenMetadataSaveFails() throws IOException {
        FileStorageService storage = storageService();
        assertStoredObjectDeletedWhenMetadataSaveFails(() -> {
            var video = multipart("lesson.mp4", "video/mp4", TestUploadFixtures.minimalMp4());
            try (var staged = storage.stageRecordedVideo(video)) {
                storage.saveStagedUpload(staged, user());
            }
        });
    }

    @Test
    void discardedStagedVideoLeavesNoStoredFile() throws IOException {
        FileStorageService storage = storageService();
        var staged = storage
                .stageRecordedVideo(multipart("lesson.mp4", "video/mp4", TestUploadFixtures.minimalMp4()));
        staged.close();
        assertNoStoredFiles();
    }

    @Test
    void aWebmLessonVideoAcceptsVideoCodecsAndRejectsAnAudioOnlyUpload() throws IOException {
        FileStorageService storage = storageService();
        byte[] webm = TestUploadFixtures.minimalWebm();
        assertThatThrownBy(() -> storage.stageRecordedVideo(multipart("lesson.webm", "audio/webm", webm)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getReason()).contains("Unsupported video content type");
                });
        assertNoStoredFiles();

        when(uploadedFileRepository.save(any(UploadedFile.class))).thenAnswer(call -> call.getArgument(0));
        UploadedFile video;
        try (var staged = storage.stageRecordedVideo(multipart("lesson.webm", "video/webm;codecs=vp9", webm))) {
            video = storage.saveStagedUpload(staged, user());
        }
        assertThat(video.getContentType()).isEqualTo("video/webm");
        assertThat(uploadRoot.resolve("videos").resolve(video.getStoredFileName())).hasBinaryContent(webm);
    }

    @Test
    void practiceCheckUploadsAreOnlyReadIntoMemory() throws IOException {
        FileStorageService.PracticeUpload upload = storageService()
                .readPracticeAudio(multipart("voice.webm", "audio/webm;codecs=opus", TestUploadFixtures.minimalWebm()));

        assertThat(upload.data()).isEqualTo(TestUploadFixtures.minimalWebm());
        assertThat(upload.contentType()).isEqualTo("audio/webm");
        assertNoStoredFiles();
    }

    @Test
    void postSaveTransactionRollbackDeletesStagedScreenshotAndVideoWhileCommitKeepsThem() throws IOException {
        FileStorageService storage = storageService();
        when(uploadedFileRepository.save(any(UploadedFile.class))).thenAnswer(call -> call.getArgument(0));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            try (var staged = storage.stageSupportScreenshot(
                    multipart("screen.png", "image/png", TestUploadFixtures.minimalPng()))) {
                storage.saveStagedUpload(staged, user());
            }
            throw new IllegalStateException("later write failed");
        })).isInstanceOf(IllegalStateException.class);
        assertNoStoredFiles();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            try (var staged = storage.stageRecordedVideo(
                    multipart("lesson.mp4", "video/mp4", TestUploadFixtures.minimalMp4()))) {
                storage.saveStagedUpload(staged, user());
            }
            throw new IllegalStateException("later write failed");
        })).isInstanceOf(IllegalStateException.class);
        assertNoStoredFiles();

        UploadedFile committed = transaction.execute(status -> {
            try (var staged = storage.stageSupportScreenshot(
                    multipart("keep.png", "image/png", TestUploadFixtures.minimalPng()))) {
                return storage.saveStagedUpload(staged, user());
            }
        });
        assertThat(storage.loadSupportScreenshot(committed).resource().getContentAsByteArray())
                .isEqualTo(TestUploadFixtures.minimalPng());
    }

    /** Saving the metadata fails after the file was written (reaching save proves it), so nothing is left on disk. */
    private void assertStoredObjectDeletedWhenMetadataSaveFails(ThrowingCallable upload) throws IOException {
        doThrow(new IllegalStateException(METADATA_FAILURE_MESSAGE)).when(uploadedFileRepository).save(any(UploadedFile.class));

        assertThatThrownBy(upload)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(METADATA_FAILURE_MESSAGE);

        assertNoStoredFiles();
    }

    private void assertNoStoredFiles() throws IOException {
        try (Stream<Path> files = Files.walk(uploadRoot)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    private FileStorageService storageService() {
        UploadProperties properties = new UploadProperties();
        properties.setDirectory(uploadRoot.resolve("uploads").toString());
        properties.setRecordedVideoDirectory(uploadRoot.resolve("videos").toString());
        return new FileStorageService(uploadedFileRepository, properties, mock(EntityManager.class));
    }

    private static MockMultipartFile multipart(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content);
    }

    private static User user() {
        User user = new User();
        user.setId(1L);
        user.setEmail("admin@example.com");
        user.setDisplayName("Admin");
        user.setPasswordHash("hash");
        return user;
    }
}
