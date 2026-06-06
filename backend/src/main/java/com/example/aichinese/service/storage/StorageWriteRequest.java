package com.example.aichinese.service.storage;

import java.nio.file.Path;
import java.util.Objects;

public record StorageWriteRequest(
        Path baseDirectory,
        String storedFileName,
        String invalidTargetMessage,
        String storageFailureMessage) {

    public StorageWriteRequest {
        baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory");
        storedFileName = requireText(storedFileName, "storedFileName");
        invalidTargetMessage = requireText(invalidTargetMessage, "invalidTargetMessage");
        storageFailureMessage = requireText(storageFailureMessage, "storageFailureMessage");
    }

    private static String requireText(String value, String fieldName) {
        String stripped = Objects.requireNonNull(value, fieldName).strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return stripped;
    }
}
