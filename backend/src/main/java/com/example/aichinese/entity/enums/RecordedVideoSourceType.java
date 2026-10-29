package com.example.aichinese.entity.enums;

import java.util.Optional;

public enum RecordedVideoSourceType {

    UPLOADED_FILE,

    EXTERNAL_URL;

    public static Optional<RecordedVideoSourceType> tryFrom(String value) {
        return EnumLookups.tryFrom(RecordedVideoSourceType.class, value);
    }
}
