package com.example.aichinese.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    private static final DataSize MAX_CONFIGURED_FILE_SIZE = DataSize.ofGigabytes(10);
    private static final long MAX_CONFIGURED_FILE_SIZE_BYTES = MAX_CONFIGURED_FILE_SIZE.toBytes();

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
        this.maxFileSize = requireValidSize(maxFileSize, "Maximum upload file size");
    }

    public DataSize getVerificationDocumentMaxFileSize() {
        return verificationDocumentMaxFileSize;
    }

    public void setVerificationDocumentMaxFileSize(DataSize verificationDocumentMaxFileSize) {
        this.verificationDocumentMaxFileSize = requireValidSize(
                verificationDocumentMaxFileSize,
                "Maximum verification document file size");
    }

    public DataSize getAiAudioMaxFileSize() {
        return aiAudioMaxFileSize;
    }

    public void setAiAudioMaxFileSize(DataSize aiAudioMaxFileSize) {
        this.aiAudioMaxFileSize = requireValidSize(
                aiAudioMaxFileSize,
                "Maximum AI audio file size");
    }

    public DataSize getAiImageMaxFileSize() {
        return aiImageMaxFileSize;
    }

    public void setAiImageMaxFileSize(DataSize aiImageMaxFileSize) {
        this.aiImageMaxFileSize = requireValidSize(
                aiImageMaxFileSize,
                "Maximum AI image file size");
    }

    public String getRecordedVideoDirectory() {
        return recordedVideoDirectory;
    }

    public void setRecordedVideoDirectory(String recordedVideoDirectory) {
        this.recordedVideoDirectory = requireText(
                recordedVideoDirectory,
                "Recorded video upload directory must not be blank.");
    }

    public DataSize getMaxVideoFileSize() {
        return maxVideoFileSize;
    }

    public void setMaxVideoFileSize(DataSize maxVideoFileSize) {
        this.maxVideoFileSize = requireValidSize(maxVideoFileSize, "Maximum recorded video file size");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }

    private static DataSize requireValidSize(DataSize value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " must be configured.");
        }
        long bytes = value.toBytes();
        if (bytes <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero.");
        }
        if (bytes > MAX_CONFIGURED_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    label + " must be at most " + MAX_CONFIGURED_FILE_SIZE.toGigabytes() + " GB.");
        }
        return value;
    }
}
