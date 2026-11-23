package com.example.aichinese.config;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record SecurityProperties(
        String secret,
        long expiration,
        long refreshTokenExpiration,
        String refreshTokenCookieName,
        boolean refreshTokenCookieSecure,
        String refreshTokenSameSite) {

    /** An RFC 6265 cookie-name token: letters, digits and {@code ! # $ % & ' * + - . ^ _ ` | ~}. */
    private static final Pattern COOKIE_NAME = Pattern.compile("[A-Za-z0-9!#$%&'*+\\-.^_`|~]+");
    /** Cap TTLs at 10 years so epoch-millisecond additions in JWT/cookie builders can never overflow. */
    private static final long MAX_TTL_MILLIS = 3650L * 24 * 60 * 60 * 1000;

    public SecurityProperties {
        secret = normalizeSecret(secret);
        if (expiration <= 0 || expiration > MAX_TTL_MILLIS) {
            throw new IllegalArgumentException("JWT expiration must be between 1 ms and 10 years.");
        }
        if (refreshTokenExpiration <= 0 || refreshTokenExpiration > MAX_TTL_MILLIS) {
            throw new IllegalArgumentException("Refresh token expiration must be between 1 ms and 10 years.");
        }
        refreshTokenCookieName = requireCookieName(refreshTokenCookieName);
        refreshTokenSameSite = normalizeSameSite(refreshTokenSameSite);
        if ("None".equals(refreshTokenSameSite) && !refreshTokenCookieSecure) {
            throw new IllegalArgumentException("Refresh token cookies with SameSite=None must also set Secure=true.");
        }
    }

    @Override
    public String toString() {
        return "SecurityProperties[secret=[set]"
                + ", expiration=" + expiration + "ms"
                + ", refreshTokenExpiration=" + refreshTokenExpiration + "ms"
                + ", refreshTokenCookieName=" + refreshTokenCookieName
                + ", refreshTokenCookieSecure=" + refreshTokenCookieSecure
                + ", refreshTokenSameSite=" + refreshTokenSameSite + "]";
    }

    private static String normalizeSecret(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be blank.");
        }
        return value.strip();
    }

    private static String requireCookieName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Refresh token cookie name must not be blank.");
        }
        String normalized = value.strip();
        if (!COOKIE_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Refresh token cookie name contains an invalid character.");
        }
        return normalized;
    }

    private static String normalizeSameSite(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Refresh token SameSite policy must not be blank.");
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "strict" -> "Strict";
            case "lax" -> "Lax";
            case "none" -> "None";
            default -> throw new IllegalArgumentException("Refresh token SameSite policy must be Strict, Lax, or None.");
        };
    }
}
