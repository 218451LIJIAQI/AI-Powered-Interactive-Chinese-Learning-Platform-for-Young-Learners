package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.example.aichinese.config.UploadProperties;

/**
 * Unit coverage for {@code UploadProperties}. It pins the configured
 * directory trimming and the positive-size guard on file-size limits
 * so a malformed configuration cannot accept zero / negative byte
 * caps that would defeat the upload defence-in-depth pipeline.
 */
class UploadPropertiesTests {

    private static final String UPLOAD_DIRECTORY = "./target/uploads";
    private static final String VIDEO_DIRECTORY = "./target/videos";
    private static final String BLANK_DIRECTORY = " ";
    private static final String DIRECTORY_MESSAGE = "directory";
    private static final String FILE_SIZE_MESSAGE = "file size";
    private static final long KILOBYTE = 1024L;
    private static final long MEGABYTE = KILOBYTE * KILOBYTE;

    @Test
    void trimsConfiguredDirectoriesAndAcceptsPositiveSizes() {
        UploadProperties properties = new UploadProperties();

        properties.setDirectory("  " + UPLOAD_DIRECTORY + "  ");
        properties.setRecordedVideoDirectory("  " + VIDEO_DIRECTORY + "  ");
        properties.setMaxFileSize(DataSize.ofKilobytes(1));
        properties.setVerificationDocumentMaxFileSize(DataSize.ofKilobytes(2));
        properties.setAiAudioMaxFileSize(DataSize.ofKilobytes(3));
        properties.setAiImageMaxFileSize(DataSize.ofKilobytes(4));
        properties.setMaxVideoFileSize(DataSize.ofMegabytes(1));

        assertThat(properties.getDirectory()).isEqualTo(UPLOAD_DIRECTORY);
        assertThat(properties.getRecordedVideoDirectory()).isEqualTo(VIDEO_DIRECTORY);
        assertThat(properties.getMaxFileSize().toBytes()).isEqualTo(KILOBYTE);
        assertThat(properties.getVerificationDocumentMaxFileSize().toBytes()).isEqualTo(KILOBYTE * 2);
        assertThat(properties.getAiAudioMaxFileSize().toBytes()).isEqualTo(KILOBYTE * 3);
        assertThat(properties.getAiImageMaxFileSize().toBytes()).isEqualTo(KILOBYTE * 4);
        assertThat(properties.getMaxVideoFileSize().toBytes()).isEqualTo(MEGABYTE);
    }

    @Test
    void rejectsBlankDirectoriesAndNonPositiveUploadSizes() {
        UploadProperties properties = new UploadProperties();

        assertRejects(() -> properties.setDirectory(BLANK_DIRECTORY), DIRECTORY_MESSAGE);
        assertRejects(() -> properties.setRecordedVideoDirectory(BLANK_DIRECTORY), DIRECTORY_MESSAGE);
        assertRejects(() -> properties.setMaxFileSize(DataSize.ofBytes(0)), FILE_SIZE_MESSAGE);
        assertRejects(() -> properties.setVerificationDocumentMaxFileSize(DataSize.ofBytes(0)), FILE_SIZE_MESSAGE);
        assertRejects(() -> properties.setAiAudioMaxFileSize(DataSize.ofBytes(0)), FILE_SIZE_MESSAGE);
        assertRejects(() -> properties.setAiImageMaxFileSize(DataSize.ofBytes(0)), FILE_SIZE_MESSAGE);
        assertRejects(() -> properties.setMaxVideoFileSize(DataSize.ofBytes(-1)), FILE_SIZE_MESSAGE);
    }

    private static void assertRejects(ThrowingCallable action, String message) {
        assertThatThrownBy(action)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }
}
