package com.example.aichinese.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    private String directory = "./uploads";
    private DataSize maxFileSize = DataSize.ofMegabytes(5);
    private String recordedVideoDirectory = "./uploads/recorded-videos";
    private DataSize maxVideoFileSize = DataSize.ofMegabytes(100);

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = requireText(directory, "Upload directory must not be blank.");
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = requirePositive(maxFileSize, "Maximum upload file size must be greater than zero.");
    }

    public String getRecordedVideoDirectory() {
        return recordedVideoDirectory;
    }

    public void setRecordedVideoDirectory(String recordedVideoDirectory) {
        this.recordedVideoDirectory = requireText(recordedVideoDirectory, "Recorded video upload directory must not be blank.");
    }

    public DataSize getMaxVideoFileSize() {
        return maxVideoFileSize;
    }

    public void setMaxVideoFileSize(DataSize maxVideoFileSize) {
        this.maxVideoFileSize = requirePositive(maxVideoFileSize, "Maximum recorded video file size must be greater than zero.");
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private DataSize requirePositive(DataSize value, String message) {
        if (value == null || value.toBytes() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
