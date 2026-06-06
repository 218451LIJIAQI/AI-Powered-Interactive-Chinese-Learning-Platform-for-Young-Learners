package com.example.aichinese.service;

import java.util.Objects;

import org.springframework.core.io.Resource;

public record StoredFileDownload(
        Resource resource,
        String originalFileName,
        String contentType) {

    public StoredFileDownload {
        resource = Objects.requireNonNull(resource, "resource");
        originalFileName = optionalText(originalFileName);
        contentType = optionalText(contentType);
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
