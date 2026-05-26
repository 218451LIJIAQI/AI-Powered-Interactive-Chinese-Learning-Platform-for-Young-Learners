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

    @TempDir
    Path tempDir;

    @Test
    void deleteQuietlyDoesNotExposeAbsolutePathWhenCleanupFails(CapturedOutput output) throws Exception {
        Path managedDirectory = tempDir.resolve("sensitive-server-root").resolve("stored.pdf");
        Files.createDirectories(managedDirectory);
        Files.writeString(managedDirectory.resolve("child.txt"), "keeps the directory non-empty");

        new LocalStorageProvider().deleteQuietly(new StoredObject(managedDirectory));

        assertThat(output)
                .contains("Failed to delete stored object during upload rollback for name stored.pdf")
                .contains("Exception")
                .doesNotContain(tempDir.toString())
                .doesNotContain("sensitive-server-root")
                .doesNotContain("child.txt")
                .doesNotContain("at com.example.aichinese");
    }
}
