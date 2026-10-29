package com.example.aichinese.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.auth.AuthResponse;
import com.example.aichinese.dto.auth.AuthSessionResponse;
import com.example.aichinese.dto.auth.RefreshTokenIssue;

/**
 * The HttpOnly refresh-token cookie shared by the auth and account controllers: the response that starts a session
 * (sets the cookie, clears the legacy path) and the headers that clear it.
 */
final class RefreshCookieWriter {

    private static final String LEGACY_REFRESH_COOKIE_PATH = "/api/v1/auth";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth/refresh";

    private RefreshCookieWriter() {
    }

    /** A new session: the refresh cookie, plus clearing any cookie left at the legacy path, and the auth response. */
    static ResponseEntity<ApiResponse<AuthResponse>> sessionResponse(
            SecurityProperties properties,
            String message,
            AuthSessionResponse session) {
        RefreshTokenIssue refreshToken = session.refreshToken();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookie(properties, refreshToken).toString(),
                        clearRefreshCookie(properties, LEGACY_REFRESH_COOKIE_PATH).toString())
                .body(ApiResponse.success(message, session.authResponse()));
    }

    /** Set-Cookie values that clear the refresh cookie at both the current and the legacy path. */
    static String[] clearedCookieHeaders(SecurityProperties properties) {
        return new String[] {
                clearRefreshCookie(properties, REFRESH_COOKIE_PATH).toString(),
                clearRefreshCookie(properties, LEGACY_REFRESH_COOKIE_PATH).toString()
        };
    }

    /** A persistent cookie lives as long as the token; otherwise it is a browser-session cookie. */
    private static ResponseCookie refreshCookie(SecurityProperties properties, RefreshTokenIssue refreshToken) {
        ResponseCookie.ResponseCookieBuilder cookie = builder(properties, refreshToken.rawToken(), REFRESH_COOKIE_PATH);
        return (refreshToken.persistent() ? cookie.maxAge(refreshToken.ttl()) : cookie).build();
    }

    private static ResponseCookie clearRefreshCookie(SecurityProperties properties, String path) {
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
