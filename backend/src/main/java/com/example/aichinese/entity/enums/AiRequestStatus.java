package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum AiRequestStatus {

    SUCCESS,

    FAILED,

    UNAVAILABLE;

    private static final Map<String, AiRequestStatus> LOOKUP =
            EnumLookups.indexByName(AiRequestStatus.class);

    public static Optional<AiRequestStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static AiRequestStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "AI request status is required.",
                () -> "Invalid AI request status. Allowed values are SUCCESS, FAILED, or UNAVAILABLE.");
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    public boolean isFailure() {
        return this != SUCCESS;
    }

    public boolean isUnavailable() {
        return this == UNAVAILABLE;
    }
}
