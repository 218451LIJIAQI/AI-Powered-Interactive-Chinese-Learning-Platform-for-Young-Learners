package com.example.aichinese.service.storage;

import java.util.Objects;

import com.example.aichinese.entity.UploadedFile;

public record StorageReadRequest(
        String configuredDirectory,
        UploadedFile uploadedFile,
        String notFoundMessage) {

    public StorageReadRequest {
        configuredDirectory = requireText(configuredDirectory, "configuredDirectory");
        uploadedFile = Objects.requireNonNull(uploadedFile, "uploadedFile");
        notFoundMessage = requireText(notFoundMessage, "notFoundMessage");
    }

    private static String requireText(String value, String fieldName) {
        String stripped = Objects.requireNonNull(value, fieldName).strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return stripped;
    }
}
