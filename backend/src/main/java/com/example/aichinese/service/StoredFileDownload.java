package com.example.aichinese.service;

import java.util.Objects;

import org.springframework.core.io.Resource;

public record StoredFileDownload(
        Resource resource,
        String originalFileName,
        String contentType) {

    public StoredFileDownload {
        Objects.requireNonNull(resource, "resource");
    }
}
