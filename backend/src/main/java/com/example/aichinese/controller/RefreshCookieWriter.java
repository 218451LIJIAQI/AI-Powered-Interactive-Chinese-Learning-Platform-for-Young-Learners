package com.example.aichinese.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import com.example.aichinese.config.RefreshCookieOriginFilter;
import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.auth.AuthResponse;
import com.example.aichinese.dto.auth.AuthSessionResponse;
import com.example.aichinese.dto.auth.RefreshTokenIssue;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * The HttpOnly refresh-token cookie shared by the auth and account controllers: reading the cookie, starting a session
 * and ending it using the single normalized cookie name from {@link SecurityProperties}.
 */
@Component
@RequiredArgsConstructor
final class RefreshCookieWriter {

    private final SecurityProperties properties;

    /** The raw refresh token from the configured HttpOnly cookie, or {@code null} when absent. */
    String readRefreshToken(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, properties.refreshTokenCookieName());
        return cookie == null ? null : cookie.getValue();
    }

    /** A new session: the refresh cookie and the auth response. */
    ResponseEntity<ApiResponse<AuthResponse>> sessionResponse(
            HttpServletRequest request,
            String message,
            AuthSessionResponse session) {
        RefreshTokenIssue token = session.refreshToken();
        ResponseCookie.ResponseCookieBuilder cookie = cookie(request, token.rawToken());
        if (token.persistent()) {
            cookie.maxAge(token.ttl());
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.build().toString())
                .body(ApiResponse.success(message, session.authResponse()));
    }

    /** A session that ended: the refresh cookie is cleared. */
    ResponseEntity<ApiResponse<Void>> clearedCookieResponse(HttpServletRequest request, String message) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(request, "").maxAge(0).build().toString())
                .body(ApiResponse.success(message, null));
    }

    private ResponseCookie.ResponseCookieBuilder cookie(HttpServletRequest request, String value) {
        return ResponseCookie.from(properties.refreshTokenCookieName(), value)
                .httpOnly(true)
                .secure(properties.refreshTokenCookieSecure())
                .sameSite(properties.refreshTokenSameSite())
                .path(request.getContextPath() + RefreshCookieOriginFilter.REFRESH_PATH);
    }
}
