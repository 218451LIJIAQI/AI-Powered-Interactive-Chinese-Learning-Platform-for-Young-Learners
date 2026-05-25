package com.example.aichinese.dto.auth;

import java.util.Objects;

public record RefreshSessionResponse(
        AuthResponse authResponse,
        RefreshTokenIssue refreshToken) {

    public RefreshSessionResponse {
        authResponse = Objects.requireNonNull(authResponse, "authResponse must not be null");
        refreshToken = Objects.requireNonNull(refreshToken, "refreshToken must not be null");
    }
}
