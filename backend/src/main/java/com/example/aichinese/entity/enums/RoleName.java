package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum RoleName {

    STUDENT,

    TEACHER,

    ADMIN;

    public static final String AUTHORITY_PREFIX = "ROLE_";

    private static final Map<String, RoleName> LOOKUP = EnumLookups.indexByName(RoleName.class);

    public static Optional<RoleName> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static RoleName fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Role is required.",
                () -> "Invalid role. Allowed values are STUDENT, TEACHER, or ADMIN.");
    }

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }
}
