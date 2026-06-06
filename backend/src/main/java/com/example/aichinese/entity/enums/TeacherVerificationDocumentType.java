package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum TeacherVerificationDocumentType {

    CERTIFICATE,

    QUALIFICATION,

    IDENTITY,

    OTHER;

    private static final Map<String, TeacherVerificationDocumentType> LOOKUP =
            EnumLookups.indexByName(TeacherVerificationDocumentType.class);

    public static Optional<TeacherVerificationDocumentType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static TeacherVerificationDocumentType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid document type. Allowed values are CERTIFICATE, "
                        + "QUALIFICATION, IDENTITY, or OTHER."));
    }
}
