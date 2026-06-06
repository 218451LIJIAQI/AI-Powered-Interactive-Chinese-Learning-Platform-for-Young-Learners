package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.example.aichinese.service.LocalStorageProvider;
import com.example.aichinese.service.storage.StoredObject;

@ExtendWith(OutputCaptureExtension.class)
class LocalStorageProviderTests {

    private static final String SENSITIVE_DIRECTORY = "sensitive-server-root";
    private static final String STORED_FILE_NAME = "stored.pdf";
    private static final String CHILD_FILE_NAME = "child.txt";
    private static final String CHILD_FILE_CONTENT = "keeps the directory non-empty";
    private static final String SAFE_ROLLBACK_LOG =
            "Failed to delete stored object during upload rollback for name " + STORED_FILE_NAME;

    @TempDir
    Path tempDir;

    private final LocalStorageProvider storageProvider = new LocalStorageProvider();

    @Test
    void deleteQuietlyDoesNotExposeAbsolutePathWhenCleanupFails(CapturedOutput output) throws Exception {
        Path managedDirectory = tempDir.resolve(SENSITIVE_DIRECTORY).resolve(STORED_FILE_NAME);
        Files.createDirectories(managedDirectory);
        Files.writeString(managedDirectory.resolve(CHILD_FILE_NAME), CHILD_FILE_CONTENT);

        storageProvider.deleteQuietly(new StoredObject(managedDirectory));

        assertThat(output)
                .contains(SAFE_ROLLBACK_LOG)
                .contains("Exception")
                .doesNotContain(tempDir.toString())
                .doesNotContain(SENSITIVE_DIRECTORY)
                .doesNotContain(CHILD_FILE_NAME)
                .doesNotContain("at com.example.aichinese");
    }
}
