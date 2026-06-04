package com.example.aichinese.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Strongly typed binding for the {@code app.upload.*} configuration block.
 */
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    private static final DataSize MAX_CONFIGURED_FILE_SIZE = DataSize.ofGigabytes(10);

    private String directory = "./uploads";
    private DataSize maxFileSize = DataSize.ofMegabytes(5);
    private DataSize verificationDocumentMaxFileSize = DataSize.ofMegabytes(5);
    private DataSize aiAudioMaxFileSize = DataSize.ofMegabytes(10);
    private DataSize aiImageMaxFileSize = DataSize.ofMegabytes(10);
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

    public DataSize getVerificationDocumentMaxFileSize() {
        return verificationDocumentMaxFileSize;
    }

    public void setVerificationDocumentMaxFileSize(DataSize verificationDocumentMaxFileSize) {
        this.verificationDocumentMaxFileSize = requirePositive(
                verificationDocumentMaxFileSize,
                "Maximum verification document file size must be greater than zero.");
    }

    public DataSize getAiAudioMaxFileSize() {
        return aiAudioMaxFileSize;
    }

    public void setAiAudioMaxFileSize(DataSize aiAudioMaxFileSize) {
        this.aiAudioMaxFileSize = requirePositive(
                aiAudioMaxFileSize,
                "Maximum AI audio file size must be greater than zero.");
    }

    public DataSize getAiImageMaxFileSize() {
        return aiImageMaxFileSize;
    }

    public void setAiImageMaxFileSize(DataSize aiImageMaxFileSize) {
        this.aiImageMaxFileSize = requirePositive(
                aiImageMaxFileSize,
                "Maximum AI image file size must be greater than zero.");
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

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }

    private static DataSize requirePositive(DataSize value, String message) {
        if (value == null || value.toBytes() <= 0) {
            throw new IllegalArgumentException(message);
        }
        if (value.toBytes() > MAX_CONFIGURED_FILE_SIZE.toBytes()) {
            throw new IllegalArgumentException(
                    message.replace("greater than zero", "at most " + MAX_CONFIGURED_FILE_SIZE.toGigabytes() + " GB"));
        }
        return value;
    }
}
