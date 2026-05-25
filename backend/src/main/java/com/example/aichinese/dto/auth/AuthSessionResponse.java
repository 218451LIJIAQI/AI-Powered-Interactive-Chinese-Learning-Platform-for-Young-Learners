package com.example.aichinese.dto.auth;

import java.util.Objects;

public record AuthSessionResponse(
        AuthResponse authResponse,
        RefreshTokenIssue refreshToken) {

    public AuthSessionResponse {
        authResponse = Objects.requireNonNull(authResponse, "authResponse must not be null");
        refreshToken = Objects.requireNonNull(refreshToken, "refreshToken must not be null");
    }
}
