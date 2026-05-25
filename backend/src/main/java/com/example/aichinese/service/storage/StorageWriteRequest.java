package com.example.aichinese.service.storage;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Storage-provider write request. The local provider interprets
 * {@link #baseDirectory} as a filesystem root; object-store providers can map it
 * to a bucket prefix while preserving the same service contract.
 */
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
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return value;
    }
}
