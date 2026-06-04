package com.example.aichinese.dto.auth;

import java.nio.charset.StandardCharsets;

final class AuthDtoValidation {

    static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private AuthDtoValidation() {
    }

    static boolean isWithinBcryptByteLimit(String value) {
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_PASSWORD_BYTES;
    }
}
