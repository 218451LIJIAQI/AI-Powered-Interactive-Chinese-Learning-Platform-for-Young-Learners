package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.example.aichinese.config.UploadProperties;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.UploadedFileRepository;
import com.example.aichinese.service.AiFileStorageService;
import com.example.aichinese.service.FileStorageService;
import com.example.aichinese.service.RecordedVideoStorageService;
import com.example.aichinese.service.storage.StorageProvider;
import com.example.aichinese.service.storage.StorageWriteRequest;
import com.example.aichinese.service.storage.StoredObject;
import com.example.aichinese.support.TestUploadFixtures;

class UploadStorageServiceCleanupTests {

    private static final String FILE_PART = "file";
    private static final String VERIFICATION_FILE_NAME = "verification.pdf";
    private static final String RECORDED_VIDEO_FILE_NAME = "lesson.mp4";
    private static final String AUDIO_FILE_NAME = "voice.webm";
    private static final String VERIFICATION_CONTENT_TYPE = "application/pdf";
    private static final String VIDEO_CONTENT_TYPE = "video/mp4";
    private static final String AUDIO_CONTENT_TYPE = "audio/webm";
    private static final String METADATA_FAILURE_MESSAGE = "metadata failed";
    private static final String UPLOAD_DIRECTORY = "target/test-uploads";
    private static final String RECORDED_VIDEO_DIRECTORY = "target/test-video-uploads";
    private static final long USER_ID = 1L;
    private static final String USER_EMAIL = "teacher@example.com";
    private static final String DISPLAY_NAME = "Teacher";
    private static final String PASSWORD_HASH = "hash";

    @Test
    void verificationDocumentUploadDeletesStoredObjectWhenMetadataSaveFails() {
        StoredObject storedObject = storedObject(VERIFICATION_FILE_NAME);
        UploadedFileRepository uploadedFileRepository = failingRepository();
        StorageProvider storageProvider = storingProvider(storedObject);
        FileStorageService service = new FileStorageService(uploadedFileRepository, uploadProperties(), storageProvider);

        assertThatThrownBy(() -> service.storeVerificationDocument(
                multipart(VERIFICATION_FILE_NAME, VERIFICATION_CONTENT_TYPE, TestUploadFixtures.minimalPdf()),
                user()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(METADATA_FAILURE_MESSAGE);

        verify(storageProvider).deleteQuietly(storedObject);
    }

    @Test
    void recordedVideoUploadDeletesStoredObjectWhenMetadataSaveFails() {
        StoredObject storedObject = storedObject(RECORDED_VIDEO_FILE_NAME);
        UploadedFileRepository uploadedFileRepository = failingRepository();
        StorageProvider storageProvider = storingProvider(storedObject);
        RecordedVideoStorageService service = new RecordedVideoStorageService(
                uploadedFileRepository,
                uploadProperties(),
                storageProvider);

        assertThatThrownBy(() -> service.storeRecordedVideo(
                multipart(RECORDED_VIDEO_FILE_NAME, VIDEO_CONTENT_TYPE, TestUploadFixtures.minimalMp4()),
                user()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(METADATA_FAILURE_MESSAGE);

        verify(storageProvider).deleteQuietly(storedObject);
    }

    @Test
    void aiFeedbackUploadDeletesStoredObjectWhenMetadataSaveFails() {
        StoredObject storedObject = storedObject(AUDIO_FILE_NAME);
        UploadedFileRepository uploadedFileRepository = failingRepository();
        StorageProvider storageProvider = storingProvider(storedObject);
        PlatformTransactionManager transactionManager = new TestTransactionManager();
        AiFileStorageService service = new AiFileStorageService(
                uploadProperties(),
                uploadedFileRepository,
                transactionManager,
                storageProvider);

        assertThatThrownBy(() -> service.storePronunciationAudio(
                multipart(AUDIO_FILE_NAME, AUDIO_CONTENT_TYPE, TestUploadFixtures.minimalWebm()),
                user()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(METADATA_FAILURE_MESSAGE);

        verify(storageProvider).deleteQuietly(storedObject);
    }

    private static UploadedFileRepository failingRepository() {
        UploadedFileRepository repository = mock(UploadedFileRepository.class);
        doThrow(new IllegalStateException(METADATA_FAILURE_MESSAGE)).when(repository).save(any(UploadedFile.class));
        return repository;
    }

    private static StorageProvider storingProvider(StoredObject storedObject) {
        StorageProvider storageProvider = mock(StorageProvider.class);
        when(storageProvider.store(any(MockMultipartFile.class), any(StorageWriteRequest.class)))
                .thenReturn(storedObject);
        return storageProvider;
    }

    private static StoredObject storedObject(String filename) {
        return new StoredObject(Path.of(filename));
    }

    private static MockMultipartFile multipart(String filename, String contentType, byte[] content) {
        return new MockMultipartFile(FILE_PART, filename, contentType, content);
    }

    private static UploadProperties uploadProperties() {
        UploadProperties properties = new UploadProperties();
        properties.setDirectory(UPLOAD_DIRECTORY);
        properties.setRecordedVideoDirectory(RECORDED_VIDEO_DIRECTORY);
        return properties;
    }

    private static User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(USER_EMAIL);
        user.setDisplayName(DISPLAY_NAME);
        user.setPasswordHash(PASSWORD_HASH);
        return user;
    }

    private static final class TestTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
