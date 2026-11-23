package com.example.aichinese.dto.auth;

import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A new refresh token: its raw value (cookie only), its lifetime, whether the cookie outlives the browser session, and
 * the browser session it belongs to ({@code sessionId}: the rotation chain that started at the sign-in), which the
 * access tokens of that browser name.
 */
public record RefreshTokenIssue(
        @JsonIgnore
        String rawToken,
        Duration ttl,
        boolean persistent,
        @JsonIgnore
        String sessionId) {

    @Override
    public String toString() {
        return "RefreshTokenIssue[rawToken=***REDACTED***, ttl=" + ttl + ", persistent=" + persistent + "]";
    }
}
