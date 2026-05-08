package com.example.aichinese.entity.enums;

import java.util.Arrays;

public enum TeacherVerificationDocumentType {
    CERTIFICATE,
    QUALIFICATION,
    IDENTITY,
    OTHER;

    public static TeacherVerificationDocumentType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }

        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid document type. Allowed values are CERTIFICATE, QUALIFICATION, IDENTITY, or OTHER."));
    }
}
