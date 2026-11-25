package com.example.aichinese.entity.enums;

/** The two roles: a learner (STUDENT) and an admin. */
public enum RoleName {

    STUDENT,

    ADMIN;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }
}
