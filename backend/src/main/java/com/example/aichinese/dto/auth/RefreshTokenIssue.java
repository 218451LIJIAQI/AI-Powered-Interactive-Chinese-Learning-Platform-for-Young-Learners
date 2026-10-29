package com.example.aichinese.dto.auth;

import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** A new refresh token: its raw value (cookie only), its lifetime, and whether the cookie outlives the browser session. */
public record RefreshTokenIssue(
        @JsonIgnore
        String rawToken,
        Duration ttl,
        boolean persistent) {

    @Override
    public String toString() {
        return "RefreshTokenIssue[rawToken=***REDACTED***, ttl=" + ttl + ", persistent=" + persistent + "]";
    }
}
