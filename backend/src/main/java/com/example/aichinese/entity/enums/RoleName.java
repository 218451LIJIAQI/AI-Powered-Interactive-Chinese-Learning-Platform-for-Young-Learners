package com.example.aichinese.entity.enums;

import java.util.Optional;

public enum RoleName {

    STUDENT,

    TEACHER,

    ADMIN;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    public static Optional<RoleName> tryFrom(String value) {
        return EnumLookups.tryFrom(RoleName.class, value);
    }

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }
}
