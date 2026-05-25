package com.example.aichinese.dto.auth;

import java.time.Duration;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record RefreshTokenIssue(
        @JsonIgnore
        String rawToken,
        Duration ttl) {

    public RefreshTokenIssue {
        String token = Objects.requireNonNull(rawToken, "rawToken must not be null");
        if (token.isBlank()) {
            throw new IllegalArgumentException("rawToken must not be blank");
        }
        rawToken = token;
        ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    @Override
    public String toString() {
        return "RefreshTokenIssue[rawToken=***REDACTED***, ttl=" + ttl + "]";
    }
}
