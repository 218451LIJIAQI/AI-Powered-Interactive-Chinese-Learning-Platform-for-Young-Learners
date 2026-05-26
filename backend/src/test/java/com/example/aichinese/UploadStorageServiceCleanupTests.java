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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.example.aichinese.support.TestUploadFixtures;
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

class UploadStorageServiceCleanupTests {

    @Test
    void verificationDocumentUploadDeletesStoredObjectWhenMetadataSaveFails() {
        StoredObject storedObject = new StoredObject(Path.of("verification.pdf"));
        UploadedFileRepository uploadedFileRepository = failingRepository();
        StorageProvider storageProvider = storingProvider(storedObject);
        FileStorageService service = new FileStorageService(uploadedFileRepository, uploadProperties(), storageProvider);

        assertThatThrownBy(() -> service.storeVerificationDocument(
                new MockMultipartFile("file", "verification.pdf", "application/pdf", TestUploadFixtures.minimalPdf()),
                user()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("metadata failed");

        verify(storageProvider).deleteQuietly(storedObject);
    }

    @Test
    void recordedVideoUploadDeletesStoredObjectWhenMetadataSaveFails() {
        StoredObject storedObject = new StoredObject(Path.of("video.mp4"));
        UploadedFileRepository uploadedFileRepository = failingRepository();
        StorageProvider storageProvider = storingProvider(storedObject);
        RecordedVideoStorageService service = new RecordedVideoStorageService(uploadedFileRepository, uploadProperties(), storageProvider);

        assertThatThrownBy(() -> service.storeRecordedVideo(
                new MockMultipartFile("file", "lesson.mp4", "video/mp4", TestUploadFixtures.minimalMp4()),
                user()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("metadata failed");

        verify(storageProvider).deleteQuietly(storedObject);
    }

    @Test
    void aiFeedbackUploadDeletesStoredObjectWhenMetadataSaveFails() {
        StoredObject storedObject = new StoredObject(Path.of("audio.webm"));
        UploadedFileRepository uploadedFileRepository = failingRepository();
        StorageProvider storageProvider = storingProvider(storedObject);
        PlatformTransactionManager transactionManager = new TestTransactionManager();
        AiFileStorageService service = new AiFileStorageService(
                uploadProperties(),
                uploadedFileRepository,
                transactionManager,
                storageProvider);

        assertThatThrownBy(() -> service.storePronunciationAudio(
                new MockMultipartFile("file", "voice.webm", "audio/webm", TestUploadFixtures.minimalWebm()),
                user()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("metadata failed");

        verify(storageProvider).deleteQuietly(storedObject);
    }

    private UploadedFileRepository failingRepository() {
        UploadedFileRepository repository = mock(UploadedFileRepository.class);
        doThrow(new IllegalStateException("metadata failed")).when(repository).save(any(UploadedFile.class));
        return repository;
    }

    private StorageProvider storingProvider(StoredObject storedObject) {
        StorageProvider storageProvider = mock(StorageProvider.class);
        when(storageProvider.store(any(MockMultipartFile.class), any(StorageWriteRequest.class)))
                .thenReturn(storedObject);
        return storageProvider;
    }

    private UploadProperties uploadProperties() {
        UploadProperties properties = new UploadProperties();
        properties.setDirectory("target/test-uploads");
        properties.setRecordedVideoDirectory("target/test-video-uploads");
        return properties;
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setEmail("teacher@example.com");
        user.setDisplayName("Teacher");
        user.setPasswordHash("hash");
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
