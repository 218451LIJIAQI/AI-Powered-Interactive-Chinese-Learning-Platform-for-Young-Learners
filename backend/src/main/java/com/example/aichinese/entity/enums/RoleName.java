package com.example.aichinese.entity.enums;

import java.util.Optional;

/** The two roles: a learner (STUDENT) and an admin. */
public enum RoleName {

    STUDENT,

    ADMIN;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    public static Optional<RoleName> tryFrom(String value) {
        return EnumLookups.tryFrom(RoleName.class, value);
    }

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }
}
