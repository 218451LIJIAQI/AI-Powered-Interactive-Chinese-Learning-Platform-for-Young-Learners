package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum RecordedVideoSourceType {

    UPLOADED_FILE,

    EXTERNAL_URL;

    private static final Map<String, RecordedVideoSourceType> LOOKUP =
            EnumLookups.indexByName(RecordedVideoSourceType.class);

    public static Optional<RecordedVideoSourceType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static RecordedVideoSourceType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Recorded video source type is required.",
                () -> "Invalid recorded video source type. Allowed values are UPLOADED_FILE or EXTERNAL_URL.");
    }

    public boolean isUploadedFile() {
        return this == UPLOADED_FILE;
    }

    public boolean isExternalUrl() {
        return this == EXTERNAL_URL;
    }
}
