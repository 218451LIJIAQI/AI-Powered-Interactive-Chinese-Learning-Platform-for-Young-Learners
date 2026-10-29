package com.example.aichinese.dto.auth;

public record AuthSessionResponse(
        AuthResponse authResponse,
        RefreshTokenIssue refreshToken) {
}
