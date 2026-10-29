package com.example.aichinese.dto.auth;

import static com.example.aichinese.dto.DtoNormalization.requireNonBlank;

public record AuthResponse(String token) {

    public AuthResponse {
        token = requireNonBlank(token, "token");
    }

    @Override
    public String toString() {
        return "AuthResponse[token=***REDACTED***]";
    }
}
