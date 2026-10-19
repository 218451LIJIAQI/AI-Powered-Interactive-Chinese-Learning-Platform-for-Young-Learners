package com.example.aichinese.controller;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

import com.example.aichinese.config.SecurityProperties;

/** Builds the HttpOnly refresh-token cookie shared by the auth and account controllers. */
final class RefreshCookieWriter {

    static final String LEGACY_REFRESH_COOKIE_PATH = "/api/v1/auth";
    static final String REFRESH_COOKIE_PATH = "/api/v1/auth/refresh";

    private RefreshCookieWriter() {
    }

    static ResponseCookie refreshCookie(SecurityProperties properties, String rawToken, Duration maxAge) {
        return builder(properties, rawToken, REFRESH_COOKIE_PATH).maxAge(maxAge).build();
    }

    static ResponseCookie clearRefreshCookie(SecurityProperties properties, String path) {
        return builder(properties, "", path).maxAge(0).build();
    }

    private static ResponseCookie.ResponseCookieBuilder builder(SecurityProperties properties, String value, String path) {
        return ResponseCookie.from(properties.refreshTokenCookieName(), value)
                .httpOnly(true)
                .secure(properties.refreshTokenCookieSecure())
                .sameSite(properties.refreshTokenSameSite())
                .path(path);
    }
}
