package com.example.aichinese.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Strongly-typed binding for the {@code app.upload.*} configuration block.
 *
 * <p>Captures the local filesystem directories where uploaded
 * artefacts are stored and the per-file size caps the upload service
 * enforces. Setters validate eagerly so a mis-configured directory
 * (blank string) or a non-positive size cap (zero / negative) fails
 * fast at binding time rather than on first upload.</p>
 *
 * <ul>
 *   <li>{@link #directory} — generic upload root (verification
 *       documents, profile pictures, etc.).</li>
 *   <li>{@link #recordedVideoDirectory} — separate root for recorded
 *       lesson videos so the video files (often hundreds of MB) can
 *       be hosted on a different volume / storage tier.</li>
 * </ul>
 *
 * <p>Sizes are bound as Spring's {@link DataSize}, accepting the usual
 * {@code 5MB} / {@code 100MB} suffixes; the JavaBean form is required
 * because Spring Boot relaxed-binding only supports setter-based
 * configuration for {@code DataSize}.</p>
 */
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
