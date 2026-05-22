package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.example.aichinese.config.UploadProperties;

class UploadPropertiesTests {

    @Test
    void trimsConfiguredDirectoriesAndAcceptsPositiveSizes() {
        UploadProperties properties = new UploadProperties();

        properties.setDirectory("  ./target/uploads  ");
        properties.setRecordedVideoDirectory("  ./target/videos  ");
        properties.setMaxFileSize(DataSize.ofKilobytes(1));
        properties.setMaxVideoFileSize(DataSize.ofMegabytes(1));

        assertThat(properties.getDirectory()).isEqualTo("./target/uploads");
        assertThat(properties.getRecordedVideoDirectory()).isEqualTo("./target/videos");
        assertThat(properties.getMaxFileSize().toBytes()).isEqualTo(1024);
        assertThat(properties.getMaxVideoFileSize().toBytes()).isEqualTo(1024 * 1024);
    }

    @Test
    void rejectsBlankDirectoriesAndNonPositiveUploadSizes() {
        UploadProperties properties = new UploadProperties();

        assertThatThrownBy(() -> properties.setDirectory(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("directory");
        assertThatThrownBy(() -> properties.setRecordedVideoDirectory(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("directory");
        assertThatThrownBy(() -> properties.setMaxFileSize(DataSize.ofBytes(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file size");
        assertThatThrownBy(() -> properties.setMaxVideoFileSize(DataSize.ofBytes(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file size");
    }
}
