package com.example.aichinese.dto.auth;

public record AuthResponse(String token) {

    @Override
    public String toString() {
        return "AuthResponse[token=***REDACTED***]";
    }
}
