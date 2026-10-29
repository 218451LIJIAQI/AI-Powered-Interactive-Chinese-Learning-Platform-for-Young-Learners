package com.example.aichinese.entity.enums;

import java.util.Optional;

public enum TeacherVerificationDocumentType {

    /** Teaching certificate (required). */
    CERTIFICATE,

    /** Degree certificate (optional). */
    QUALIFICATION,

    /** Identity document (required). */
    IDENTITY,

    OTHER;

    public static Optional<TeacherVerificationDocumentType> tryFrom(String value) {
        return EnumLookups.tryFrom(TeacherVerificationDocumentType.class, value);
    }
}
