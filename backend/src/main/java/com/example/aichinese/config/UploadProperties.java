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
        this.directory = directory;
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public String getRecordedVideoDirectory() {
        return recordedVideoDirectory;
    }

    public void setRecordedVideoDirectory(String recordedVideoDirectory) {
        this.recordedVideoDirectory = recordedVideoDirectory;
    }

    public DataSize getMaxVideoFileSize() {
        return maxVideoFileSize;
    }

    public void setMaxVideoFileSize(DataSize maxVideoFileSize) {
        this.maxVideoFileSize = maxVideoFileSize;
    }
}
